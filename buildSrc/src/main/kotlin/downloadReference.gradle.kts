import it.skrape.core.htmlDocument
import it.skrape.fetcher.HttpFetcher
import it.skrape.fetcher.response
import it.skrape.fetcher.skrape
import it.skrape.selects.html5.*

task("downloadReference") {
    description = "Downloads information from the latest Nix manual"
    outputs.upToDateWhen { false }

    doLast {
        val builtinFunctions = skrape(HttpFetcher) {
            request {
                url = "https://nixos.org/manual/nix/stable/language/builtins.html"
            }
            response {
                htmlDocument {
                    dt {
                        findAll {
                            map {
                                val names = it.text
                                    .split(";")
                                    .map { it.split(Regex("\\s")).first() }
//                                val name = it.a {
//                                    code {
//                                        findAll {
//                                            if (isEmpty()) {
//                                                null
//                                            }
//                                            else {
//                                                first().text
//                                            }
//                                        }
//                                    }
//                                }
                                val description = it.siblings[it.siblings.indexOf(it) + 1]
                                names
                            }
                        }
                    }
                }
            }
        }
        println(builtinFunctions)
//        "https://nixos.org/manual/nix/stable/language/builtin-constants.html"
    }
}
