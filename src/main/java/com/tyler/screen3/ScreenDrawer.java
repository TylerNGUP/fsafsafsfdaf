package com.tyler.screen3;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class ScreenDrawer {

    // 定义可绘制对象的接口
    interface Drawable {
        void draw(Graphics2D g2d);
    }

    // 矩形类实现Drawable
    static class DrawableRectangle implements Drawable {
        Rectangle rect;
        Color color;

        DrawableRectangle(int x, int y, int width, int height) {
            this.rect = new Rectangle(x, y, width, height);
            this.color = Color.RED;
        }

        @Override
        public void draw(Graphics2D g2d) {
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.drawRect(rect.x, rect.y, rect.width, rect.height);
        }
    }

    // 文字类实现Drawable
    static class DrawableText implements Drawable {
        String text;
        Point position;
        Color color;
        Font font;

        DrawableText(String text, Point position) {
            this.text = text;
            this.position = position;
            this.color = Color.BLUE;
            this.font = new Font("Arial", Font.BOLD, 24);
        }

        @Override
        public void draw(Graphics2D g2d) {
            g2d.setColor(color);
            g2d.setFont(font);
            g2d.drawString(text, position.x, position.y);
        }
    }

    private static final ArrayList<Drawable> drawables = new ArrayList<>();
    private static JFrame frame;
    private static boolean drawingMode = false;
    private static boolean textMode = false; // 文字模式标志
    private static Point startPoint;
    private static TrayIcon trayIcon;
    private static boolean forceFocus = false;

    // 记录修饰键状态
    private static boolean ctrlPressed = false;
    private static boolean altPressed = false;
    private static boolean shiftPressed = false;

    public static void main(String[] args) {
        // 启用窗口透明度支持
        System.setProperty("sun.java2d.uiScale", "1.0");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                setupSystemTray();
                createWindow();
                registerGlobalHotkeys();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Error: " + e.getMessage(), "Screen Drawer Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void setupSystemTray() {
        if (!SystemTray.isSupported()) {
            System.out.println("System tray not supported!");
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();

        // 创建托盘菜单
        PopupMenu popup = new PopupMenu();

        // 添加"激活绘图"菜单项
        MenuItem activateItem = new MenuItem("Activate Drawing");
        activateItem.addActionListener(e -> enterDrawingMode());

        // 添加"撤销"菜单项
        MenuItem undoItem = new MenuItem("Undo Last Object");
        undoItem.addActionListener(e -> undoLastObject());

        // 添加"清除所有"菜单项
        MenuItem clearItem = new MenuItem("Clear All Objects");
        clearItem.addActionListener(e -> clearAllObjects());

        // 添加"退出绘图模式"菜单项
        MenuItem exitDrawingItem = new MenuItem("Exit Drawing Mode");
        exitDrawingItem.addActionListener(e -> exitDrawingMode());

        // 添加"退出"菜单项
        MenuItem exitItem = new MenuItem("Exit Program");
        exitItem.addActionListener(e -> {
            if (SystemTray.isSupported()) {
                SystemTray traySystem = SystemTray.getSystemTray();
                traySystem.remove(trayIcon);
            }
            System.exit(0);
        });

        popup.add(activateItem);
        popup.add(undoItem);
        popup.add(clearItem);
        popup.add(exitDrawingItem);
        popup.addSeparator();
        popup.add(exitItem);

        // 创建托盘图标
        Image image = createTrayIconImage();
        trayIcon = new TrayIcon(image, "Screen Drawing Tool", popup);
        trayIcon.setImageAutoSize(true);

        // 添加双击激活
        trayIcon.addActionListener(e -> enterDrawingMode());

        try {
            tray.add(trayIcon);
        } catch (AWTException ex) {
            System.err.println("Failed to add tray icon: " + ex.getMessage());
        }
    }

    private static Image createTrayIconImage() {
        // 创建16x16红色矩形图标
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // 透明背景
        g2d.setColor(new Color(0, 0, 0, 0));
        g2d.fillRect(0, 0, 16, 16);

        // 红色矩形
        g2d.setColor(Color.RED);
        g2d.fillRect(3, 3, 10, 10);

        // 白色边框
        g2d.setColor(Color.WHITE);
        g2d.drawRect(3, 3, 10, 10);

        g2d.dispose();
        return image;
    }

    private static void createWindow() {
        frame = new JFrame();
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // 调整透明度，使用非完全透明值以提高焦点保持能力
        try {
            if (GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
                frame.setBackground(new Color(0, 0, 0, 5)); // 几乎透明但非完全透明
            } else {
                System.out.println("Window transparency not supported, using opaque window");
                frame.setBackground(Color.WHITE);
            }
        } catch (Exception e) {
            System.out.println("Setting window transparency failed: " + e.getMessage());
            frame.setBackground(Color.WHITE);
        }

        // 设置窗口大小为所有屏幕的组合区域
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gds = ge.getScreenDevices();

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (GraphicsDevice gd : gds) {
            Rectangle bounds = gd.getDefaultConfiguration().getBounds();
            minX = Math.min(minX, bounds.x);
            minY = Math.min(minY, bounds.y);
            maxX = Math.max(maxX, bounds.x + bounds.width);
            maxY = Math.max(maxY, bounds.y + bounds.height);
        }

        // 设置窗口大小和位置
        frame.setSize(maxX - minX, maxY - minY);
        frame.setLocation(minX, minY);

        frame.setVisible(false);

        // 添加绘图面板
        JPanel drawingPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();

                // 设置高质量渲染
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                // 绘制所有对象
                for (Drawable drawable : drawables) {
                    drawable.draw(g2d);
                }

                // 绘制当前正在绘制的矩形
                if (drawingMode && startPoint != null && !textMode) {
                    Point currentPoint = MouseInfo.getPointerInfo().getLocation();
                    SwingUtilities.convertPointFromScreen(currentPoint, this);

                    int x = Math.min(startPoint.x, currentPoint.x);
                    int y = Math.min(startPoint.y, currentPoint.y);
                    int width = Math.abs(startPoint.x - currentPoint.x);
                    int height = Math.abs(startPoint.y - currentPoint.y);

                    // 绘制矩形边框
                    g2d.setColor(new Color(255, 0, 0, 220));
                    g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.drawRect(x, y, width, height);
                }
                g2d.dispose();
            }
        };

        drawingPanel.setOpaque(false);
        frame.setContentPane(drawingPanel);

        // 添加鼠标监听器到面板
        drawingPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (drawingMode && e.getButton() == MouseEvent.BUTTON1) {
                    if (textMode) {
                        // 文字模式：弹出文本输入对话框
                        String text = JOptionPane.showInputDialog(frame, "Enter text:", "Text Input", JOptionPane.PLAIN_MESSAGE);
                        if (text != null && !text.trim().isEmpty()) {
                            drawables.add(new DrawableText(text, e.getPoint()));
                            System.out.println("Text added: '" + text + "' at " + e.getPoint());
                            trayIcon.displayMessage("Screen Drawing Tool", "Text added: " + text, TrayIcon.MessageType.INFO);
                            frame.repaint();
                        }
                    } else {
                        // 矩形模式：记录起点
                        startPoint = e.getPoint();
                        System.out.println("Mouse pressed at: " + startPoint);
                        frame.requestFocus();
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (drawingMode && startPoint != null && e.getButton() == MouseEvent.BUTTON1 && !textMode) {
                    Point endPoint = e.getPoint();
                    int x = Math.min(startPoint.x, endPoint.x);
                    int y = Math.min(startPoint.y, endPoint.y);
                    int width = Math.abs(startPoint.x - endPoint.x);
                    int height = Math.abs(startPoint.y - endPoint.y);

                    if (width > 5 && height > 5) {
                        drawables.add(new DrawableRectangle(x, y, width, height));
                        System.out.println("Rectangle added: " + drawables.get(drawables.size() - 1));
                        trayIcon.displayMessage("Screen Drawing Tool", "New rectangle added", TrayIcon.MessageType.INFO);
                    }

                    startPoint = null;
                    frame.repaint();
                }
            }
        });

        drawingPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (drawingMode && startPoint != null && !textMode) {
                    frame.repaint();
                }
            }
        });

        // 增强焦点管理
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                System.out.println("Window gained focus");
                forceFocus = false;
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                System.out.println("Window lost focus");
                if (drawingMode && !forceFocus) {
                    System.out.println("Attempting to regain focus");
                    forceFocus = true;
                    SwingUtilities.invokeLater(() -> {
                        frame.toFront();
                        frame.requestFocus();
                    });
                }
            }
        });

        // 添加键盘监听器到窗口
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // 记录修饰键状态
                if (e.getKeyCode() == KeyEvent.VK_CONTROL) ctrlPressed = true;
                if (e.getKeyCode() == KeyEvent.VK_ALT) altPressed = true;
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) shiftPressed = true;

                // 检测Ctrl+Alt+Shift+A组合 - 激活绘图模式
                if (e.getKeyCode() == KeyEvent.VK_A && ctrlPressed && altPressed && shiftPressed) {
                    System.out.println("Window shortcut triggered: Ctrl+Alt+Shift+A");
                    enterDrawingMode();
                    e.consume();
                }

                // 检测Ctrl+Alt+A组合 - 切换文字模式
                if (e.getKeyCode() == KeyEvent.VK_A && ctrlPressed && altPressed && !shiftPressed) {
                    toggleTextMode();
                    e.consume();
                }

                if (drawingMode) {
                    frame.requestFocus();

                    // ESC - 退出绘图模式
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        exitDrawingMode();
                        e.consume();
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_CONTROL) ctrlPressed = false;
                if (e.getKeyCode() == KeyEvent.VK_ALT) altPressed = false;
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) shiftPressed = false;
            }
        });
    }

    private static void registerGlobalHotkeys() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof KeyEvent) {
                KeyEvent e = (KeyEvent) event;
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    int mods = e.getModifiersEx();

                    // 记录全局修饰键状态
                    if (e.getKeyCode() == KeyEvent.VK_CONTROL) ctrlPressed = true;
                    if (e.getKeyCode() == KeyEvent.VK_ALT) altPressed = true;
                    if (e.getKeyCode() == KeyEvent.VK_SHIFT) shiftPressed = true;

                    // Ctrl+Alt+Shift+A - 激活绘图模式（全局监听）
                    if (e.getKeyCode() == KeyEvent.VK_A && ctrlPressed && altPressed && shiftPressed) {
                        System.out.println("Global shortcut triggered: Ctrl+Alt+Shift+A");
                        enterDrawingMode();
                        e.consume();
                    }

                    // Ctrl+Alt+A - 切换文字模式
                    if (e.getKeyCode() == KeyEvent.VK_A && ctrlPressed && altPressed && !shiftPressed) {
                        toggleTextMode();
                        e.consume();
                    }

                    // Ctrl+Z - 撤销
                    if (e.getKeyCode() == KeyEvent.VK_Z && ctrlPressed) {
                        undoLastObject();
                        e.consume();
                    }

                    // Ctrl+X - 清除所有
                    if (e.getKeyCode() == KeyEvent.VK_X && ctrlPressed) {
                        clearAllObjects();
                        e.consume();
                    }

                    // ESC - 退出绘图模式
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        exitDrawingMode();
                        e.consume();
                    }
                } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                    if (e.getKeyCode() == KeyEvent.VK_CONTROL) ctrlPressed = false;
                    if (e.getKeyCode() == KeyEvent.VK_ALT) altPressed = false;
                    if (e.getKeyCode() == KeyEvent.VK_SHIFT) shiftPressed = false;
                }
            }
        }, AWTEvent.KEY_EVENT_MASK);
    }

    private static void toggleTextMode() {
        if (!drawingMode) {
            // 必须先激活绘图模式
            enterDrawingMode();
        }

        textMode = !textMode;
        if (textMode) {
            // 切换到文字模式
            frame.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            trayIcon.displayMessage("Screen Drawing Tool",
                    "Text mode activated\nClick anywhere to add text\nPress Ctrl+Alt+A to switch back",
                    TrayIcon.MessageType.INFO);
        } else {
            // 切换回矩形模式
            frame.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            trayIcon.displayMessage("Screen Drawing Tool",
                    "Rectangle mode activated\nDrag mouse to draw rectangle\nPress Ctrl+Alt+A to add text",
                    TrayIcon.MessageType.INFO);
        }
    }

    private static void enterDrawingMode() {
        if (!drawingMode) {
            System.out.println("Entering drawing mode");
            drawingMode = true;
            textMode = false; // 默认是矩形模式

            // 设置十字光标
            Cursor crosshairCursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
            frame.setCursor(crosshairCursor);

            // 显示窗口并置于前端
            frame.setVisible(true);

            // 多重尝试获取焦点
            SwingUtilities.invokeLater(() -> {
                frame.toFront();
                frame.requestFocus();

                // 延迟后再次请求焦点
                Timer focusTimer = new Timer(500, evt -> {
                    frame.toFront();
                    frame.requestFocus();
                });
                focusTimer.setRepeats(false);
                focusTimer.start();
            });

            // 显示通知
            trayIcon.displayMessage("Screen Drawing Tool",
                    "Drawing mode activated\nDrag mouse to draw rectangle\nPress Ctrl+Alt+A to add text",
                    TrayIcon.MessageType.INFO);
        }
    }

    private static void exitDrawingMode() {
        if (drawingMode) {
            System.out.println("Exiting drawing mode");
            drawingMode = false;
            textMode = false;
            startPoint = null;
            frame.setVisible(false);
            frame.setCursor(Cursor.getDefaultCursor());
            trayIcon.setToolTip("Screen Drawing Tool (Ready)");
        }
    }

    private static void undoLastObject() {
        if (!drawables.isEmpty()) {
            Drawable removed = drawables.remove(drawables.size() - 1);
            System.out.println("Undo object: " + removed);
            if (drawingMode) {
                frame.repaint();
            }
            trayIcon.displayMessage("Screen Drawing Tool", "Last object removed", TrayIcon.MessageType.INFO);
        } else {
            trayIcon.displayMessage("Screen Drawing Tool", "No objects to undo", TrayIcon.MessageType.INFO);
        }
    }

    private static void clearAllObjects() {
        if (!drawables.isEmpty()) {
            drawables.clear();
            System.out.println("Clear all objects");
            if (drawingMode) {
                frame.repaint();
            }
            trayIcon.displayMessage("Screen Drawing Tool", "All objects cleared", TrayIcon.MessageType.INFO);
        } else {
            trayIcon.displayMessage("Screen Drawing Tool", "No objects to clear", TrayIcon.MessageType.INFO);
        }
    }
}