package org.nixos.idea.format;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class FormattingUtil extends AsyncDocumentFormattingService {

    private FormattingUtil() {} // Cannot be instantiated

    //region Static Methods

    static @Nullable Path filePath(@NotNull PsiFile psiFile) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        return filePath(virtualFile);
    }

    static @Nullable Path filePath(@Nullable VirtualFile virtualFile) {
        if (virtualFile == null || !virtualFile.isInLocalFileSystem()) return null;
        return virtualFile.getFileSystem().getNioPath(virtualFile);
    }

    //endregion
    //region AsyncFormattingRequest wrapper

    /**
     * Wrapper around {@link AsyncFormattingRequest} for easy access to implied information.
     */
    static final class RequestInfo {

        private final @NotNull AsyncFormattingRequest myRequest;
        private final @NotNull Document myDocument;
        private final @Nullable Path myFilePath;
        private final @NotNull Charset myFileCharset;
        private final @Nullable VirtualFile myVirtualFile;

        RequestInfo(@NotNull AsyncFormattingRequest request) {
            PsiFile psiFile = request.getContext().getContainingFile();
            VirtualFile virtualFile = psiFile.getVirtualFile();

            myRequest = request;
            myDocument = psiFile.getFileDocument();
            myFilePath = FormattingUtil.filePath(psiFile);
            myFileCharset = virtualFile != null ? virtualFile.getCharset() : EncodingManager.getInstance().getDefaultCharset();
            myVirtualFile = virtualFile;
        }

        @NotNull AsyncFormattingRequest request() {
            return myRequest;
        }

        @NotNull Document document() {
            return myDocument;
        }

        @Nullable Path filePath() {
            return myFilePath;
        }

        @NotNull Charset fileCharset() {
            return myFileCharset;
        }

        @Nullable VirtualFile virtualFile() {
            return myVirtualFile;
        }
    }

    //endregion
    //region FormattingTask

    static final class NixFormattingTask implements FormattingTask {

        private final @NotNull RequestInfo myInfo;
        private final @NotNull AsyncFormattingRequest myRequest;
        private final @NotNull GeneralCommandLine myCommandLine;
        private final @Nullable Path myTargetFile;
        private final @NotNull DocumentFreezeGuard myFreezeGuard;
        private @Nullable OSProcessHandler myHandler;
        private boolean myIsCanceled;

        /**
         * Creates a formatting task using a {@link GeneralCommandLine}.
         *
         * @param request the {@link RequestInfo} created from the given {@link AsyncFormattingRequest}
         * @param commandLine the command line used to start the external formatter
         * @param targetFile the file which is modified by the formatter, {@code null} when using stdio.
         */
        NixFormattingTask(
                @NotNull RequestInfo request,
                @NotNull GeneralCommandLine commandLine,
                @Nullable Path targetFile
        ) {
            myInfo = request;
            myRequest = request.request();
            myCommandLine = commandLine;
            myTargetFile = targetFile;

            // Use the encoding of the file, or the console default if not used for the file.
            if (targetFile == null) {
                commandLine.withCharset(request.fileCharset());
            }
            // If we don't use stdout, treat it the same as stderr.
            commandLine.withRedirectErrorStream(targetFile != null);

            // Mark document as read-only and afterward call FileDocumentManager.getInstance().saveDocument(...).
            // This avoids race conditions when user continues typing, which otherwise leads to either
            // - a "File Cache Conflict" dialog: "Changes have been made to '<path>' in memory and on disk."
            // - silently losing changes made by the user, as the formatter may overwrite them
            myFreezeGuard = new DocumentFreezeGuard(targetFile != null && targetFile.equals(request.filePath()), request.document());
            try {
                FileDocumentManager.getInstance().saveDocument(request.document());
            } catch (Throwable e) {
                myFreezeGuard.close();
                throw e;
            }
        }

        @Override
        public void run() {
            try (myFreezeGuard) {
                // Start formatter process
                synchronized (this) {
                    if (myIsCanceled) {
                        return;
                    }
                    try {
                        myHandler = new OSProcessHandler(myCommandLine);
                    } catch (ExecutionException e) {
                        myRequest.onError("NixIDEA", e.getMessage());
                        return;
                    }
                }

                // Configure output handling
                myHandler.addProcessListener(new CapturingProcessAdapter() {
                    @Override
                    public void processTerminated(@NotNull ProcessEvent event) {
                        try {
                            int exitCode = event.getExitCode();
                            if (myTargetFile == null) {
                                if (exitCode == 0) {
                                    myRequest.onTextReady(StringUtil.convertLineSeparators(getOutput().getStdout()));
                                } else {
                                    myRequest.onError("NixIDEA", StringUtil.convertLineSeparators(getOutput().getStderr()));
                                }
                            } else {
                                if (myTargetFile.equals(myInfo.filePath())) {
                                    // The formatter may have modified the file backing the document in place,
                                    // even if it failed or the request was canceled and the process got killed.
                                    // Let the VFS pick up any external change.
                                    // Passing the new text to onTextReady() instead would modify
                                    // the document in parallel to the file and could trigger the
                                    // "File Cache Conflict" dialog on the next VFS refresh.
                                    VfsUtil.markDirtyAndRefresh(false, false, false, myInfo.virtualFile());
                                }
                                if (exitCode == 0) {
                                    if (myTargetFile.equals(myInfo.filePath())) {
                                        myRequest.onTextReady(null);
                                    } else {
                                        // The formatter modified a temporary copy.
                                        // Read the result back and apply it to the document.
                                        // The platform wrote the copy with the file's charset,
                                        // and Document.setText() rejects CRLF line separators, so normalize accordingly.
                                        try {
                                            String text = Files.readString(myTargetFile, myInfo.fileCharset());
                                            myRequest.onTextReady(StringUtil.convertLineSeparators(text));
                                        } catch (IOException e) {
                                            myRequest.onError("NixIDEA", e.getMessage());
                                        }
                                    }
                                } else {
                                    // Use `getStdout()` as we enabled `withRedirectErrorStream`.
                                    myRequest.onError("NixIDEA", StringUtil.convertLineSeparators(getOutput().getStdout()));
                                }
                            }
                        } finally {
                            myFreezeGuard.unfreeze();
                        }
                    }
                });
                myHandler.startNotify();
                myFreezeGuard.keepAfterClose();

                // Write formatter input
                try {
                    OutputStream processInput = myHandler.getProcessInput();
                    if (myTargetFile == null) {
                        processInput.write(myRequest.getDocumentText().getBytes(myInfo.fileCharset()));
                    }
                    processInput.close();
                } catch (IOException e) {
                    myHandler.destroyProcess();
                    myRequest.onError("NixIDEA", e.getMessage());
                }
            }
        }

        @Override
        public boolean cancel() {
            myIsCanceled = true;
            if (myHandler != null) {
                // Do not wait for the process to die: cancel() may be called on the EDT.
                // While this introduces the potential of a race condition if another format is triggered,
                // there seems to be no good (not overly complicated) way to handle it.
                myHandler.destroyProcess();
            }
            return true;
        }

        @Override
        public boolean isRunUnderProgress() {
            return true;
        }
    }

    private static final class DocumentFreezeGuard implements AutoCloseable {

        private final @Nullable Document myDocument;
        private final AtomicBoolean myReleased = new AtomicBoolean();
        private boolean myKeepAfterClose;

        public DocumentFreezeGuard(boolean freeze, Document document) {
            if (freeze) {
                if (document.isWritable()) {
                    document.setReadOnly(true);
                    myDocument = document;
                } else {
                    myDocument = null;
                }
            } else {
                myDocument = null;
            }
        }

        private void keepAfterClose() {
            myKeepAfterClose = true;
        }

        private void unfreeze() {
            if (myReleased.compareAndSet(false, true) && myDocument != null) {
                ApplicationManager.getApplication().invokeLater(() -> myDocument.setReadOnly(false), ModalityState.any());
            }
        }

        @Override
        public void close() {
            if (!myKeepAfterClose) {
                unfreeze();
            }
        }
    }

    //endregion
    //region Instance methods (unused)

    // These methods are needed just because this class must extend AsyncDocumentFormattingService,
    // otherwise we would not have access to the protected class AsyncDocumentFormattingService.FormattingTask.

    @Override
    protected @Nullable FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest formattingRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected @NotNull String getNotificationGroupId() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected @NotNull @NlsSafe String getName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Set<Feature> getFeatures() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canFormat(@NotNull PsiFile file) {
        throw new UnsupportedOperationException();
    }

    //endregion
}
