package org.nixos.idea.base;

import com.sun.tools.javac.util.List;
import gnu.trove.TLongArrayList;
import gnu.trove.TLongIntHashMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;

@Fork(1)
@State(Scope.Benchmark)
public class MapVsIndexOf {
//  private static final long NEEDLE = 1_000_000;
  private final Random random = new Random(43);

  @Param({"1000", "100000"})
  public int size;

  @State(Scope.Benchmark)
  public static class ListData {
    private final TLongArrayList list = new TLongArrayList();

    @Setup
    public void setup(MapVsIndexOf parent) {
      parent.generateValues((value, index) -> list.add(value));
    }
  }

  @State(Scope.Benchmark)
  public static class MapData {
    private final Map<Long, Integer> map = new HashMap<>();

    @Setup
    public void setup(MapVsIndexOf parent) {
      parent.generateValues(map::put);
    }
  }

  @State(Scope.Benchmark)
  public static class TroveMapData {
    private final TLongIntHashMap map = new TLongIntHashMap();

    @Setup
    public void setup(MapVsIndexOf parent) {
      parent.generateValues(map::put);
    }
  }

  @State(Scope.Thread)
  public static class NeedleProvider {
    private long needle;

    @Setup(Level.Invocation)
    public void setup(MapVsIndexOf parent) {
      needle = (long) parent.random.nextInt() << 32;
    }
  }

  @Benchmark
  public int indexOf(ListData data, NeedleProvider needleProvider) {
    return data.list.indexOf(needleProvider.needle);
  }

  @Benchmark
  public Integer map(MapData data, NeedleProvider needleProvider) {
    return data.map.get(needleProvider.needle);
  }

  @Benchmark
  public int troveMap(TroveMapData data, NeedleProvider needleProvider) {
    return data.map.get(needleProvider.needle);
  }

  private void generateValues(ValueConsumer consumer) {
    HashSet<Long> seen = new HashSet<>();
    Random random = new Random(42);
    for (int i = 0; i < size;) {
      long value = random.nextInt();
      if (!seen.contains(value)) {
        seen.add(value);
        consumer.addValue(value, i);
        i += 1;
      }
    }
  }

  @FunctionalInterface
  private interface ValueConsumer {
    void addValue(long value, int index);
  }
}
