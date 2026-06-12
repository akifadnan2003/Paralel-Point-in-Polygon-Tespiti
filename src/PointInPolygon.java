import java.util.*;
import java.util.concurrent.*;

/**
 * Parallel Point-in-Polygon Detection
 * Uses Ray Casting algorithm parallelized with Java ExecutorService.
 * Supports both convex and concave polygons.
 */
public class PointInPolygon {

    private final double[] polyX;
    private final double[] polyY;
    private final int n;

    public PointInPolygon(double[] polyX, double[] polyY) {
        this.polyX = polyX.clone();
        this.polyY = polyY.clone();
        this.n = polyX.length;
    }

    /**
     * Ray Casting algorithm: cast a horizontal ray from (px, py) to +infinity.
     * Count how many polygon edges it crosses. Odd = inside, Even = outside.
     */
    public boolean isInside(double px, double py) {
        boolean inside = false;
        int j = n - 1;
        for (int i = 0; i < n; i++) {
            boolean yCondition = (polyY[i] > py) != (polyY[j] > py);
            if (yCondition) {
                double xIntersect = (polyX[j] - polyX[i]) * (py - polyY[i])
                        / (polyY[j] - polyY[i]) + polyX[i];
                if (px < xIntersect) {
                    inside = !inside;
                }
            }
            j = i;
        }
        return inside;
    }

    // -----------------------------------------------------------------------
    //  SEQUENTIAL VERSION
    // -----------------------------------------------------------------------
    public boolean[] sequentialCheck(double[] testX, double[] testY) {
        int m = testX.length;
        boolean[] results = new boolean[m];
        for (int i = 0; i < m; i++) {
            results[i] = isInside(testX[i], testY[i]);
        }
        return results;
    }

    // -----------------------------------------------------------------------
    //  PARALLEL VERSION  (thread-pool, work partitioned into numThreads chunks)
    // -----------------------------------------------------------------------
    public boolean[] parallelCheck(double[] testX, double[] testY, int numThreads)
            throws InterruptedException, ExecutionException {
        int m = testX.length;
        boolean[] results = new boolean[m];

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();

        int chunkSize = (m + numThreads - 1) / numThreads;
        for (int t = 0; t < numThreads; t++) {
            final int start = t * chunkSize;
            final int end = Math.min(start + chunkSize, m);
            if (start >= m) break;

            futures.add(executor.submit(() -> {
                for (int i = start; i < end; i++) {
                    results[i] = isInside(testX[i], testY[i]);
                }
            }));
        }

        for (Future<?> f : futures) f.get();
        executor.shutdown();
        return results;
    }

    // -----------------------------------------------------------------------
    //  BENCHMARK
    // -----------------------------------------------------------------------
    static long benchSequential(PointInPolygon pip, double[] tx, double[] ty, int reps)
            throws Exception {
        long best = Long.MAX_VALUE;
        for (int r = 0; r < reps; r++) {
            long t0 = System.nanoTime();
            pip.sequentialCheck(tx, ty);
            best = Math.min(best, System.nanoTime() - t0);
        }
        return best / 1_000_000;   // ms
    }

    static long benchParallel(PointInPolygon pip, double[] tx, double[] ty,
                              int threads, int reps) throws Exception {
        long best = Long.MAX_VALUE;
        for (int r = 0; r < reps; r++) {
            long t0 = System.nanoTime();
            pip.parallelCheck(tx, ty, threads);
            best = Math.min(best, System.nanoTime() - t0);
        }
        return best / 1_000_000;
    }

    // -----------------------------------------------------------------------
    //  MAIN
    // -----------------------------------------------------------------------
    public static void main(String[] args) throws Exception {

        // --- Polygon definition (concave L-shape) ---
        //
        //   (0,4)──(2,4)
        //     |       |
        //   (0,2)  (2,2)──(4,2)
        //     |               |
        //   (0,0)──────────(4,0)
        //
        double[] polyX = {0, 4, 4, 2, 2, 0};
        double[] polyY = {0, 0, 2, 2, 4, 4};

        PointInPolygon pip = new PointInPolygon(polyX, polyY);

        // --- Quick correctness check ---
        System.out.println("=== Correctness Check ===");
        double[][] inside  = {{1,1},{0.5,3},{1,3.9},{3,1}};
        double[][] outside = {{3,3},{5,1},{-1,2},{2.5,3}};
        for (double[] p : inside)
            System.out.printf("  (%.1f, %.1f) -> %s  [expected INSIDE]%n",
                p[0], p[1], pip.isInside(p[0], p[1]) ? "INSIDE" : "OUTSIDE");
        for (double[] p : outside)
            System.out.printf("  (%.1f, %.1f) -> %s  [expected OUTSIDE]%n",
                p[0], p[1], pip.isInside(p[0], p[1]) ? "INSIDE" : "OUTSIDE");

        // --- Benchmark ---
        System.out.println("\n=== Parallel Performance Benchmark ===");
        System.out.printf("%-10s  %-8s  %-14s  %-14s  %-10s%n",
                "Points", "Threads", "Sequential(ms)", "Parallel(ms)", "Speedup");
        System.out.println("--------------------------------------------------------------");

        int[] sizes   = {10_000, 100_000, 500_000, 1_000_000, 5_000_000};
        int[] threads = {1, 2, 4, 8};
        int REPS = 3;
        Random rand = new Random(42);

        for (int size : sizes) {
            double[] tx = new double[size];
            double[] ty = new double[size];
            for (int i = 0; i < size; i++) {
                tx[i] = rand.nextDouble() * 5 - 0.5;
                ty[i] = rand.nextDouble() * 5 - 0.5;
            }

            long seqMs = benchSequential(pip, tx, ty, REPS);

            for (int t : threads) {
                if (t == 1) {
                    System.out.printf("%-10d  %-8d  %-14d  %-14d  %-10.2f%n",
                            size, 1, seqMs, seqMs, 1.0);
                    continue;
                }
                long parMs = benchParallel(pip, tx, ty, t, REPS);
                double speedup = parMs == 0 ? 999 : (double) seqMs / parMs;
                System.out.printf("%-10d  %-8d  %-14d  %-14d  %-10.2f%n",
                        size, t, seqMs, parMs, speedup);
            }
            System.out.println();
        }

        // --- Summary for specific points mentioned in the report ---
        System.out.println("=== Summary Table (for report) ===");
        int[] reportSizes = {100, 1_000, 10_000, 100_000, 1_000_000};
        System.out.printf("%-12s  %-12s  %-12s  %-10s%n",
                "Points", "Seq (ms)", "Par-4t (ms)", "Speedup");
        System.out.println("----------------------------------------------------");
        for (int size : reportSizes) {
            double[] tx = new double[size];
            double[] ty = new double[size];
            for (int i = 0; i < size; i++) {
                tx[i] = rand.nextDouble() * 5 - 0.5;
                ty[i] = rand.nextDouble() * 5 - 0.5;
            }
            // For very small sizes nanoTime resolution matters — use nanos directly
            long t0 = System.nanoTime();
            for (int r = 0; r < 10; r++) pip.sequentialCheck(tx, ty);
            long seqNs = (System.nanoTime() - t0) / 10;

            long t1 = System.nanoTime();
            for (int r = 0; r < 10; r++) pip.parallelCheck(tx, ty, 4);
            long parNs = (System.nanoTime() - t1) / 10;

            double speedup = (double) seqNs / parNs;
            System.out.printf("%-12d  %-12.3f  %-12.3f  %-10.2fx%n",
                    size, seqNs / 1e6, parNs / 1e6, speedup);
        }
    }
}
