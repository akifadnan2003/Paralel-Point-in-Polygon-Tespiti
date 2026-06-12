import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Animated visualizer: shows sequential vs parallel point-in-polygon side by side.
 * Both animations run at the same tick rate — parallel finishes T× faster, visually.
 * Each thread gets its own color so the work division is obvious.
 */
public class PolygonVisualizer extends JFrame {

    // ── Polygon (concave L-shape) ─────────────────────────────────────────
    static final double[] PX = {0, 4, 4, 2, 2, 0};
    static final double[] PY = {0, 0, 2, 2, 4, 4};
    static final double   WORLD = 5.0;

    // ── Thread palette ────────────────────────────────────────────────────
    static final Color[] TCOLOR = {
        new Color(30,  120, 210),   // Thread 1 – blue
        new Color(210, 70,  50),    // Thread 2 – red
        new Color(50,  170,  70),   // Thread 3 – green
        new Color(200, 140,  20),   // Thread 4 – orange
        new Color(140,  60, 200),   // Thread 5 – purple
        new Color(20,  170, 170),   // Thread 6 – teal
        new Color(200,  90, 170),   // Thread 7 – pink
        new Color(100, 100, 100),   // Thread 8 – gray
    };
    static final Color OUTSIDE = new Color(200, 200, 200, 120);

    // ── Animation parameters ──────────────────────────────────────────────
    static final int TICK_MS  = 40;    // ms per frame
    static final int BATCH    = 80;    // points revealed per thread per tick

    // ── State ─────────────────────────────────────────────────────────────
    int numPoints  = 4000;
    int numThreads = 4;
    double[] testX, testY;
    boolean[] inside;           // ground-truth (pre-computed)

    // Animation progress
    int   seqCursor;            // sequential: next index to reveal
    int[] parCursor;            // parallel: per-thread cursor (local offset)
    int[] parStart;             // start index for each thread chunk
    int[] parEnd;               // end index for each thread chunk

    int   seqTicks, parTicks;   // elapsed ticks for timing
    boolean seqDone, parDone;
    javax.swing.Timer animTimer;

    // UI
    DrawPanel seqPanel, parPanel;
    JLabel speedupLabel, instrLabel;
    ChartPanel chart;
    JButton goBtn;
    JComboBox<Integer> threadBox;
    JTextField ptField;
    final PointInPolygon pip = new PointInPolygon(PX, PY);

    // ═════════════════════════════════════════════════════════════════════
    public PolygonVisualizer() {
        super("Paralel Point-in-Polygon — Animasyonlu Demo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildUI();
        setSize(1150, 720);
        setLocationRelativeTo(null);
    }

    // ── UI construction ───────────────────────────────────────────────────
    void buildUI() {
        getContentPane().setBackground(new Color(240, 242, 250));
        setLayout(new BorderLayout(8, 8));

        // Title bar
        JLabel title = new JLabel("Paralel Point-in-Polygon Tespiti", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(new Color(0, 60, 120));
        title.setBorder(new EmptyBorder(12, 0, 4, 0));
        add(title, BorderLayout.NORTH);

        // Two canvas panels
        seqPanel = new DrawPanel("Sıralı (Sequential)", false);
        parPanel = new DrawPanel("Paralel", true);
        JPanel canvases = new JPanel(new GridLayout(1, 2, 10, 0));
        canvases.setOpaque(false);
        canvases.setBorder(new EmptyBorder(0, 10, 0, 10));
        canvases.add(seqPanel);
        canvases.add(parPanel);
        add(canvases, BorderLayout.CENTER);

        // Bottom: controls + chart
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(0, 10, 8, 10));

        // Controls row
        JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 6));
        ctrl.setBackground(new Color(225, 228, 245));
        ctrl.setBorder(new CompoundBorder(
            new LineBorder(new Color(180, 190, 220), 1, true),
            new EmptyBorder(4, 10, 4, 10)));

        ctrl.add(bold("Nokta sayısı:"));
        ptField = new JTextField("4000", 6);
        ctrl.add(ptField);

        ctrl.add(bold("Thread sayısı:"));
        threadBox = new JComboBox<>(new Integer[]{1, 2, 4, 8});
        threadBox.setSelectedItem(4);
        ctrl.add(threadBox);

        goBtn = new JButton("▶  Yarışı Başlat");
        goBtn.setBackground(new Color(0, 110, 0));
        goBtn.setForeground(Color.WHITE);
        goBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        goBtn.setFocusPainted(false);
        goBtn.addActionListener(e -> startRace());
        ctrl.add(goBtn);

        speedupLabel = new JLabel("  Hızlanma: —  ");
        speedupLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        speedupLabel.setForeground(new Color(160, 0, 0));
        ctrl.add(speedupLabel);

        // Instruction
        instrLabel = new JLabel(
            "Thread sayısını değiştirerek 'Yarışı Başlat'a basın — paralel taraf T× daha hızlı dolar.",
            SwingConstants.CENTER);
        instrLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        instrLabel.setForeground(new Color(80, 80, 120));

        chart = new ChartPanel();
        chart.setPreferredSize(new Dimension(0, 120));

        bottom.add(ctrl,       BorderLayout.NORTH);
        bottom.add(instrLabel, BorderLayout.CENTER);
        bottom.add(chart,      BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    static JLabel bold(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        return l;
    }

    // ── Race logic ────────────────────────────────────────────────────────
    void startRace() {
        try {
            numPoints  = Math.max(100, Integer.parseInt(ptField.getText().trim()));
            numThreads = (Integer) threadBox.getSelectedItem();
        } catch (Exception ex) { return; }

        // Pre-compute ground truth
        Random rand = new Random(12345);
        testX = new double[numPoints];
        testY = new double[numPoints];
        inside = new boolean[numPoints];
        for (int i = 0; i < numPoints; i++) {
            testX[i] = rand.nextDouble() * WORLD;
            testY[i] = rand.nextDouble() * WORLD;
            inside[i] = pip.isInside(testX[i], testY[i]);
        }

        // Partition for parallel
        parStart  = new int[numThreads];
        parEnd    = new int[numThreads];
        parCursor = new int[numThreads];
        int chunk = (numPoints + numThreads - 1) / numThreads;
        for (int t = 0; t < numThreads; t++) {
            parStart[t]  = t * chunk;
            parEnd[t]    = Math.min(parStart[t] + chunk, numPoints);
            parCursor[t] = 0;
        }
        seqCursor = 0;
        seqTicks  = 0; parTicks = 0;
        seqDone   = false; parDone = false;

        seqPanel.reset("Sıralı (Sequential)");
        parPanel.reset("Paralel (" + numThreads + " Thread)");
        speedupLabel.setText("  Yarış devam ediyor...  ");
        goBtn.setEnabled(false);

        if (animTimer != null) animTimer.stop();
        animTimer = new javax.swing.Timer(TICK_MS, e -> tick());
        animTimer.start();
    }

    void tick() {
        // Advance sequential
        if (!seqDone) {
            seqTicks++;
            int end = Math.min(seqCursor + BATCH, numPoints);
            seqCursor = end;
            seqPanel.revealedSeq = seqCursor;
            seqPanel.elapsedTicks = seqTicks;
            if (seqCursor >= numPoints) seqDone = true;
        }

        // Advance parallel (each thread gets BATCH per tick)
        if (!parDone) {
            parTicks++;
            boolean allDone = true;
            for (int t = 0; t < numThreads; t++) {
                int available = (parEnd[t] - parStart[t]) - parCursor[t];
                if (available > 0) {
                    parCursor[t] += Math.min(BATCH, available);
                    allDone = false;
                }
            }
            parPanel.parCursor  = parCursor.clone();
            parPanel.parStart   = parStart;
            parPanel.parEnd     = parEnd;
            parPanel.numThreads = numThreads;
            parPanel.elapsedTicks = parTicks;
            if (allDone) parDone = true;
        }

        seqPanel.repaint();
        parPanel.repaint();

        if (seqDone && parDone) {
            animTimer.stop();
            goBtn.setEnabled(true);

            double speedup = seqTicks == 0 ? 1.0 : (double) seqTicks / parTicks;
            speedupLabel.setText(String.format("  Hızlanma: %.2f×  ", speedup));
            chart.add(numPoints, numThreads, speedup);
            chart.repaint();
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Drawing panel
    // ═════════════════════════════════════════════════════════════════════
    class DrawPanel extends JPanel {
        String panelTitle;
        boolean isParallel;

        // Sequential state
        int revealedSeq = 0;

        // Parallel state
        int[] parCursor, parStart, parEnd;
        int numThreads;

        int elapsedTicks = 0;

        DrawPanel(String t, boolean p) {
            panelTitle = t; isParallel = p;
            setBackground(new Color(252, 252, 255));
            setBorder(new CompoundBorder(
                new LineBorder(new Color(160, 170, 210), 2, true),
                new EmptyBorder(2, 2, 2, 2)));
        }

        void reset(String t) {
            panelTitle = t; revealedSeq = 0;
            parCursor = null; elapsedTicks = 0;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int canvasH = h - 30; // reserve bottom for labels

            // Draw polygon
            g2.setColor(new Color(100, 149, 237, 50));
            int[] sx = new int[PX.length], sy = new int[PY.length];
            for (int i = 0; i < PX.length; i++) {
                sx[i] = wx(PX[i], w);
                sy[i] = wy(PY[i], canvasH);
            }
            g2.fillPolygon(sx, sy, PX.length);
            g2.setColor(new Color(0, 60, 180));
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawPolygon(sx, sy, PX.length);

            // Draw points
            if (testX != null) {
                if (!isParallel) {
                    // Sequential: single color for inside, gray for outside
                    for (int i = 0; i < revealedSeq; i++) {
                        int px = wx(testX[i], w), py = wy(testY[i], canvasH);
                        g2.setColor(inside[i] ? TCOLOR[0] : OUTSIDE);
                        g2.fillOval(px - 3, py - 3, 6, 6);
                    }
                } else {
                    // Parallel: each thread's points in its own color
                    if (parCursor != null) {
                        for (int t = 0; t < numThreads; t++) {
                            Color col = TCOLOR[t % TCOLOR.length];
                            int end = parStart[t] + parCursor[t];
                            for (int i = parStart[t]; i < end; i++) {
                                int px = wx(testX[i], w), py = wy(testY[i], canvasH);
                                g2.setColor(inside[i] ? col : OUTSIDE);
                                g2.fillOval(px - 3, py - 3, 6, 6);
                            }
                        }
                    }
                }
            }

            // Panel title
            g2.setColor(isParallel ? new Color(0, 100, 0) : new Color(100, 0, 0));
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString(panelTitle, 10, 18);

            // Timer bar
            int totalPts = (testX == null) ? 1 : testX.length;
            int done = isParallel
                ? (parCursor == null ? 0 : sum(parCursor))
                : revealedSeq;
            float pct = (float) done / totalPts;

            g2.setColor(new Color(220, 220, 235));
            g2.fillRoundRect(10, h - 22, w - 20, 14, 7, 7);
            g2.setColor(isParallel ? new Color(30, 160, 30) : new Color(30, 30, 180));
            g2.fillRoundRect(10, h - 22, (int)((w - 20) * pct), 14, 7, 7);
            g2.setColor(Color.DARK_GRAY);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.drawString(String.format("%d / %d nokta   (%d tik)", done, totalPts, elapsedTicks),
                          14, h - 11);

            // Thread legend (parallel panel only)
            if (isParallel && numThreads > 0) {
                int lx = w - 110, ly = 24;
                for (int t = 0; t < numThreads; t++) {
                    g2.setColor(TCOLOR[t % TCOLOR.length]);
                    g2.fillRect(lx, ly + t * 17, 13, 13);
                    g2.setColor(Color.DARK_GRAY);
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                    g2.drawString("Thread " + (t + 1), lx + 17, ly + t * 17 + 11);
                }
            }
        }

        int wx(double v, int w) { return (int)(v / WORLD * w); }
        int wy(double v, int h) { return h - (int)(v / WORLD * h); }
        int sum(int[] a) { int s = 0; for (int v : a) s += v; return s; }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Speedup bar chart
    // ═════════════════════════════════════════════════════════════════════
    static class ChartPanel extends JPanel {
        // each entry: {points, threads, speedup}
        final List<double[]> data = new ArrayList<>();

        void add(int pts, int threads, double speedup) {
            data.add(new double[]{pts, threads, speedup});
            if (data.size() > 9) data.remove(0);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(235, 238, 252));
            g2.fillRect(0, 0, w, h);

            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2.setColor(new Color(0, 60, 120));
            g2.drawString("Hızlanma Geçmişi (Speedup Chart)", 8, 15);

            if (data.isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.setFont(new Font("SansSerif", Font.ITALIC, 11));
                g2.drawString("Yarışı başlatınca hızlanma buraya eklenir.", 8, 45);
                return;
            }

            int margin = 40, barW = Math.min(55, (w - margin - 20) / data.size() - 4);
            double maxS = data.stream().mapToDouble(r -> r[2]).max().orElse(1.0);
            maxS = Math.max(maxS, 1.5);
            int chartH = h - 38;

            // Axis
            g2.setColor(new Color(160, 170, 200));
            g2.drawLine(margin, 18, margin, h - 18);
            g2.drawLine(margin, h - 18, w - 10, h - 18);

            // Y-axis labels
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(new Color(80, 80, 120));
            for (int v = 1; v <= (int) Math.ceil(maxS); v++) {
                int y = h - 18 - (int)(v / maxS * chartH);
                g2.setColor(new Color(200, 200, 220));
                g2.drawLine(margin, y, w - 10, y);
                g2.setColor(new Color(80, 80, 120));
                g2.drawString(v + "×", 2, y + 4);
            }

            // Bars
            for (int i = 0; i < data.size(); i++) {
                double[] r = data.get(i);
                int threads = (int) r[1];
                int barH = (int)(r[2] / maxS * chartH);
                int x = margin + 10 + i * (barW + 6);
                int y = h - 18 - barH;

                Color col = TCOLOR[threads == 1 ? 0 : threads == 2 ? 1 : threads == 4 ? 2 : 3];
                g2.setColor(col);
                g2.fillRoundRect(x, y, barW, barH, 4, 4);
                g2.setColor(col.darker());
                g2.drawRoundRect(x, y, barW, barH, 4, 4);

                // Value inside bar
                if (barH > 18) {
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                    g2.drawString(String.format("%.1f×", r[2]), x + 3, y + 14);
                }

                // Label below
                g2.setColor(new Color(60, 60, 100));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                String lbl = threads + "t/" + fmt((int) r[0]);
                g2.drawString(lbl, x + 1, h - 5);
            }
        }

        static String fmt(int n) {
            if (n >= 1_000_000) return n / 1_000_000 + "M";
            if (n >= 1_000) return n / 1_000 + "K";
            return "" + n;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PolygonVisualizer().setVisible(true));
    }
}
