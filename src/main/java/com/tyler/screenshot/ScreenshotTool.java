package com.tyler.screenshot;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.Stack;

// 主类
public class ScreenshotTool {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // 设置系统默认外观
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new MainFrame().setVisible(true);
        });
    }
}

// 主窗口类
class MainFrame extends JFrame {
    private JButton captureButton;
    private JButton viewPinnedButton;
    private TrayIcon trayIcon;
    private SystemTray tray;
    private List<PinnedWindow> pinnedWindows = new ArrayList<>();

    public MainFrame() {
        initComponents();
        initSystemTray();
        registerGlobalHotkey();
    }

    private void initComponents() {
        setTitle("简易截图工具");
        setSize(300, 150);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new FlowLayout(FlowLayout.CENTER, 20, 20));

        captureButton = new JButton("截图");
        captureButton.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        captureButton.setPreferredSize(new Dimension(120, 40));
        captureButton.addActionListener(e -> startScreenshot());

        viewPinnedButton = new JButton("查看钉图");
        viewPinnedButton.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        viewPinnedButton.setPreferredSize(new Dimension(120, 40));
        viewPinnedButton.addActionListener(e -> togglePinnedWindows());

        add(captureButton);
        add(viewPinnedButton);

        // 添加快捷键支持 (F1)
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "startScreenshot");
        getRootPane().getActionMap().put("startScreenshot", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startScreenshot();
            }
        });
    }

    private void initSystemTray() {
        if (SystemTray.isSupported()) {
            tray = SystemTray.getSystemTray();

            // 尝试加载图标资源
            URL iconUrl = getClass().getResource("/icon.png");
            Image image;

            if (iconUrl != null) {
                image = Toolkit.getDefaultToolkit().getImage(iconUrl);
            } else {
                // 创建一个简单的16x16蓝色方块作为默认图标
                image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = (Graphics2D) image.getGraphics();
                g2d.setColor(Color.BLUE);
                g2d.fillRect(0, 0, 16, 16);
                g2d.dispose();
            }

            PopupMenu popup = new PopupMenu();
            MenuItem captureItem = new MenuItem("截图");
            MenuItem viewPinnedItem = new MenuItem("查看钉图");
            MenuItem exitItem = new MenuItem("退出");

            captureItem.addActionListener(e -> startScreenshot());
            viewPinnedItem.addActionListener(e -> togglePinnedWindows());
            exitItem.addActionListener(e -> System.exit(0));

            popup.add(captureItem);
            popup.add(viewPinnedItem);
            popup.addSeparator();
            popup.add(exitItem);

            trayIcon = new TrayIcon(image, "简易截图工具", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> setVisible(true));

            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                e.printStackTrace();
            }

            // 最小化到托盘
            addWindowStateListener(e -> {
                if (e.getNewState() == ICONIFIED) {
                    setVisible(false);
                    trayIcon.displayMessage("提示", "程序已最小化到托盘", TrayIcon.MessageType.INFO);
                }
            });
        }
    }

    private void registerGlobalHotkey() {
        try {
            KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            manager.addKeyEventDispatcher(e -> {
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    if (e.isControlDown() && e.isAltDown() && e.getKeyCode() == KeyEvent.VK_Q) {
                        startScreenshot();
                        return true;
                    }
                }
                return false;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startScreenshot() {
        setVisible(false);
        // 延迟执行，确保窗口完全隐藏
        Timer timer = new Timer(300, e -> {
            dispose();
            new ScreenshotOverlay(this).setVisible(true);
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void togglePinnedWindows() {
        for (PinnedWindow window : pinnedWindows) {
            window.setVisible(!window.isVisible());
        }
    }

    public void addPinnedWindow(PinnedWindow window) {
        pinnedWindows.add(window);
    }
}

// 截图覆盖层类 - 改进以支持多显示器
class ScreenshotOverlay extends JFrame {
    MainFrame mainFrame;
    private BufferedImage screenImage;
    private SelectionPanel selectionPanel;
    private Rectangle allScreensBounds; // 所有屏幕的组合边界

    public ScreenshotOverlay(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
        captureScreen();
    }

    private void initComponents() {
        setUndecorated(true);

        // 获取所有屏幕的组合边界
        allScreensBounds = getCombinedScreenBounds();

        // 设置窗口大小为所有屏幕的组合大小
        setSize(allScreensBounds.width, allScreensBounds.height);

        // 将窗口位置设置为所有屏幕的左上角
        setLocation(allScreensBounds.x, allScreensBounds.y);

        setBackground(new Color(0, 0, 0, 128));

        selectionPanel = new SelectionPanel(this);
        add(selectionPanel);

        // ESC键退出
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                    mainFrame.setVisible(true);
                }
            }
        });
    }

    // 获取所有屏幕的组合边界
    private Rectangle getCombinedScreenBounds() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screens = ge.getScreenDevices();

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (GraphicsDevice screen : screens) {
            GraphicsConfiguration gc = screen.getDefaultConfiguration();
            Rectangle bounds = gc.getBounds();

            minX = Math.min(minX, bounds.x);
            minY = Math.min(minY, bounds.y);
            maxX = Math.max(maxX, bounds.x + bounds.width);
            maxY = Math.max(maxY, bounds.y + bounds.height);
        }

        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    private void captureScreen() {
        try {
            Robot robot = new Robot();

            // 捕获所有屏幕的组合区域
            screenImage = robot.createScreenCapture(allScreensBounds);
            selectionPanel.setScreenImage(screenImage);
            repaint();
        } catch (AWTException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "截图失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            dispose();
            mainFrame.setVisible(true);
        }
    }

    public void finishSelection(Rectangle selectionRect) {
        dispose();
        new EditFrame(mainFrame, screenImage, selectionRect).setVisible(true);
    }
}

// 选择面板类
class SelectionPanel extends JPanel {
    private ScreenshotOverlay parent;
    private BufferedImage screenImage;
    private Rectangle selectionRect = new Rectangle();
    private Point startPoint = null;
    private Point endPoint = null;
    private boolean isSelecting = false;

    public SelectionPanel(ScreenshotOverlay parent) {
        this.parent = parent;
        setOpaque(false);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                startPoint = e.getPoint();
                selectionRect.setLocation(startPoint);
                selectionRect.setSize(0, 0);
                isSelecting = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isSelecting) {
                    isSelecting = false;
                    if (selectionRect.width > 5 && selectionRect.height > 5) {
                        parent.finishSelection(selectionRect);
                    } else {
                        parent.dispose();
                        parent.mainFrame.setVisible(true);
                    }
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isSelecting) {
                    endPoint = e.getPoint();
                    updateSelectionRect();
                    repaint();
                }
            }
        });
    }

    public void setScreenImage(BufferedImage image) {
        this.screenImage = image;
    }

    private void updateSelectionRect() {
        int x = Math.min(startPoint.x, endPoint.x);
        int y = Math.min(startPoint.y, endPoint.y);
        int width = Math.abs(endPoint.x - startPoint.x);
        int height = Math.abs(endPoint.y - startPoint.y);
        selectionRect.setBounds(x, y, width, height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (screenImage != null) {
            g.drawImage(screenImage, 0, 0, this);
        }

        if (isSelecting && selectionRect.width > 0 && selectionRect.height > 0) {
            // 绘制选择区域
            Graphics2D g2d = (Graphics2D) g;

            // 绘制半透明背景
            g2d.setColor(new Color(0, 0, 0, 100));
            g2d.fillRect(0, 0, getWidth(), selectionRect.y);
            g2d.fillRect(0, selectionRect.y, selectionRect.x, selectionRect.height);
            g2d.fillRect(selectionRect.x + selectionRect.width, selectionRect.y,
                    getWidth() - selectionRect.x - selectionRect.width, selectionRect.height);
            g2d.fillRect(0, selectionRect.y + selectionRect.height, getWidth(),
                    getHeight() - selectionRect.y - selectionRect.height);

            // 绘制选择框边框
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRect(selectionRect.x, selectionRect.y, selectionRect.width, selectionRect.height);

            // 绘制尺寸信息
            String sizeInfo = selectionRect.width + " x " + selectionRect.height;
            g2d.setColor(Color.WHITE);
            g2d.fillRect(selectionRect.x, selectionRect.y - 20, g2d.getFontMetrics().stringWidth(sizeInfo) + 10, 20);
            g2d.setColor(Color.BLACK);
            g2d.drawString(sizeInfo, selectionRect.x + 5, selectionRect.y - 5);
        }
    }
}

// 编辑窗口类
class EditFrame extends JFrame {
    private MainFrame mainFrame;
    private BufferedImage originalImage;
    private BufferedImage editedImage;
    private EditPanel editPanel;
    private JToolBar toolBar;
    private JButton saveButton, copyButton, pinButton, closeButton, undoButton, eraserButton;
    private JButton rectangleButton, circleButton, lineButton, arrowButton, textButton;
    private JComboBox<String> colorComboBox;
    private JSpinner strokeSpinner, eraserSizeSpinner;
    private List<Shape> shapes = new ArrayList<>();
    private Shape currentShape = null;
    private Point startPoint = null;
    private Point endPoint = null;
    private String currentTool = "rectangle";
    private Color currentColor = Color.RED;
    private int currentStroke = 2;
    private int currentEraserSize = 20; // 橡皮擦大小
    private Stack<List<Shape>> historyStack = new Stack<>();
    private static final int MAX_HISTORY = 100; // 最多可撤销100次

    public EditFrame(MainFrame mainFrame, BufferedImage screenImage, Rectangle selectionRect) {
        this.mainFrame = mainFrame;
        this.originalImage = screenImage.getSubimage(selectionRect.x, selectionRect.y,
                selectionRect.width, selectionRect.height);
        this.editedImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = editedImage.createGraphics();
        g2d.drawImage(originalImage, 0, 0, null);
        g2d.dispose();

        initComponents();
    }

    private void initComponents() {
        setTitle("编辑截图");
        setSize(originalImage.getWidth() + 20, originalImage.getHeight() + 120);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        createToolBar();

        editPanel = new EditPanel();
        add(editPanel, BorderLayout.CENTER);

        // ESC键退出
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                }
            }
        });

        // Ctrl+Z快捷键
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "undo");
        getRootPane().getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                undo();
            }
        });
    }

    private void createToolBar() {
        toolBar = new JToolBar();
        toolBar.setFloatable(false);

        undoButton = new JButton("撤销");
        undoButton.addActionListener(e -> undo());

        saveButton = new JButton("保存");
        saveButton.addActionListener(e -> saveImage());

        copyButton = new JButton("复制");
        copyButton.addActionListener(e -> copyToClipboard());

        pinButton = new JButton("钉在桌面");
        pinButton.addActionListener(e -> pinImage());

        closeButton = new JButton("关闭");
        closeButton.addActionListener(e -> dispose());

        rectangleButton = new JButton("矩形");
        rectangleButton.addActionListener(e -> setCurrentTool("rectangle"));

        circleButton = new JButton("圆形");
        circleButton.addActionListener(e -> setCurrentTool("circle"));

        lineButton = new JButton("直线");
        lineButton.addActionListener(e -> setCurrentTool("line"));

        arrowButton = new JButton("箭头");
        arrowButton.addActionListener(e -> setCurrentTool("arrow"));

        textButton = new JButton("文字");
        textButton.addActionListener(e -> setCurrentTool("text"));

        eraserButton = new JButton("橡皮擦");
        eraserButton.addActionListener(e -> setCurrentTool("eraser"));

        String[] colors = {"红色", "绿色", "蓝色", "黄色", "黑色"};
        colorComboBox = new JComboBox<>(colors);
        colorComboBox.addActionListener(e -> setCurrentColor());

        strokeSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 10, 1));
        strokeSpinner.addChangeListener(e -> currentStroke = (int) strokeSpinner.getValue());

        eraserSizeSpinner = new JSpinner(new SpinnerNumberModel(20, 10, 100, 10));
        eraserSizeSpinner.addChangeListener(e -> currentEraserSize = (int) eraserSizeSpinner.getValue());

        toolBar.add(undoButton);
        toolBar.addSeparator();
        toolBar.add(saveButton);
        toolBar.add(copyButton);
        toolBar.add(pinButton);
        toolBar.add(closeButton);
        toolBar.addSeparator();
        toolBar.add(rectangleButton);
        toolBar.add(circleButton);
        toolBar.add(lineButton);
        toolBar.add(arrowButton);
        toolBar.add(textButton);
        toolBar.add(eraserButton);
        toolBar.addSeparator();
        toolBar.add(new JLabel("颜色:"));
        toolBar.add(colorComboBox);
        toolBar.add(new JLabel(" 粗细:"));
        toolBar.add(strokeSpinner);
        toolBar.addSeparator();
        toolBar.add(new JLabel("橡皮擦大小:"));
        toolBar.add(eraserSizeSpinner);

        add(toolBar, BorderLayout.NORTH);
    }

    private void setCurrentTool(String tool) {
        this.currentTool = tool;
        rectangleButton.setEnabled(!tool.equals("rectangle"));
        circleButton.setEnabled(!tool.equals("circle"));
        lineButton.setEnabled(!tool.equals("line"));
        arrowButton.setEnabled(!tool.equals("arrow"));
        textButton.setEnabled(!tool.equals("text"));
        eraserButton.setEnabled(!tool.equals("eraser"));
    }

    private void setCurrentColor() {
        String colorName = (String) colorComboBox.getSelectedItem();
        switch (colorName) {
            case "红色":
                currentColor = Color.RED;
                break;
            case "绿色":
                currentColor = Color.GREEN;
                break;
            case "蓝色":
                currentColor = Color.BLUE;
                break;
            case "黄色":
                currentColor = Color.YELLOW;
                break;
            case "黑色":
                currentColor = Color.BLACK;
                break;
        }
    }

    private void saveHistory() {
        if (historyStack.size() >= MAX_HISTORY) {
            historyStack.remove(0);
        }
        historyStack.push(new ArrayList<>(shapes));
    }

    private void undo() {
        if (!historyStack.isEmpty()) {
            shapes = historyStack.pop();
            editPanel.repaint();
        }
    }

    private void saveImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("保存截图");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".png");
            }

            @Override
            public String getDescription() {
                return "PNG 图片 (*.png)";
            }
        });

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getParentFile(), file.getName() + ".png");
            }

            try {
                // 合并所有形状到图像
                Graphics2D g2d = editedImage.createGraphics();
                g2d.drawImage(originalImage, 0, 0, null);
                for (Shape shape : shapes) {
                    shape.draw(g2d);
                }
                g2d.dispose();

                ImageIO.write(editedImage, "PNG", file);
                JOptionPane.showMessageDialog(this, "截图已保存到: " + file.getAbsolutePath(),
                        "保存成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void copyToClipboard() {
        try {
            // 合并所有形状到图像
            BufferedImage copiedImage = new BufferedImage(editedImage.getWidth(), editedImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = copiedImage.createGraphics();
            g2d.drawImage(originalImage, 0, 0, null);
            for (Shape shape : shapes) {
                shape.draw(g2d);
            }
            g2d.dispose();

            // 创建剪贴板图像
            Transferable transferable = new TransferableImage(copiedImage);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(transferable, null);

            JOptionPane.showMessageDialog(this, "截图已复制到剪贴板",
                    "成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "复制失败: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private class TransferableImage implements Transferable {
        private BufferedImage image;

        public TransferableImage(BufferedImage image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (isDataFlavorSupported(flavor)) {
                return image;
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }

    private void pinImage() {
        // 合并所有形状到图像
        BufferedImage pinnedImage = new BufferedImage(editedImage.getWidth(), editedImage.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = pinnedImage.createGraphics();
        g2d.drawImage(originalImage, 0, 0, null);
        for (Shape shape : shapes) {
            shape.draw(g2d);
        }
        g2d.dispose();

        PinnedWindow pinnedWindow = new PinnedWindow(pinnedImage);
        mainFrame.addPinnedWindow(pinnedWindow);
        pinnedWindow.setVisible(true);

        dispose();
    }

    // 编辑面板类
    private class EditPanel extends JPanel {
        private JTextField textField;

        public EditPanel() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(originalImage.getWidth(), originalImage.getHeight()));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    // 保存当前正在编辑的文本
                    if (textField != null) {
                        String text = textField.getText();
                        if (!text.isEmpty()) {
                            shapes.add(new TextShape(textField.getLocation(), text, currentColor, 14));
                            saveHistory(); // 保存历史记录
                        }
                        remove(textField);
                        textField = null;
                        repaint();
                    }

                    startPoint = e.getPoint();

                    if (currentTool.equals("text")) {
                        createTextField(e.getPoint());
                    } else if (currentTool.equals("eraser")) {
                        eraseShapes(e.getPoint());
                    } else {
                        saveHistory(); // 保存历史记录
                        switch (currentTool) {
                            case "rectangle":
                                currentShape = new RectangleShape(startPoint, currentColor, currentStroke);
                                break;
                            case "circle":
                                currentShape = new CircleShape(startPoint, currentColor, currentStroke);
                                break;
                            case "line":
                                currentShape = new LineShape(startPoint, currentColor, currentStroke);
                                break;
                            case "arrow":
                                currentShape = new ArrowShape(startPoint, currentColor, currentStroke);
                                break;
                        }
                        shapes.add(currentShape);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (currentShape != null && !currentTool.equals("text") && !currentTool.equals("eraser")) {
                        endPoint = e.getPoint();
                        currentShape.setEndPoint(endPoint);
                        saveHistory(); // 保存历史记录
                        currentShape = null;
                        repaint();
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (currentShape != null && !currentTool.equals("text") && !currentTool.equals("eraser")) {
                        endPoint = e.getPoint();
                        currentShape.setEndPoint(endPoint);
                        repaint();
                    } else if (currentTool.equals("eraser")) {
                        eraseShapes(e.getPoint());
                    }
                }
            });
        }

        private void createTextField(Point point) {
            if (textField != null) {
                remove(textField);
            }

            textField = new JTextField();
            textField.setForeground(currentColor);
            textField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            textField.setBorder(BorderFactory.createLineBorder(currentColor));
            textField.setOpaque(false);
            textField.setSize(100, 25);
            textField.setLocation(point);

            textField.addActionListener(e -> {
                String text = textField.getText();
                if (!text.isEmpty()) {
                    shapes.add(new TextShape(point, text, currentColor, 14));
                    saveHistory(); // 保存历史记录
                }
                remove(textField);
                textField = null;
                repaint();
            });

            textField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    // 关键修复：检查textField是否为null
                    if (textField != null) {
                        String text = textField.getText();
                        if (!text.isEmpty()) {
                            shapes.add(new TextShape(point, text, currentColor, 14));
                            saveHistory(); // 保存历史记录
                        }
                        remove(textField);
                        textField = null;
                        repaint();
                    }
                }
            });

            add(textField);
            textField.requestFocus();
            repaint();
        }

        private void eraseShapes(Point point) {
            int size = currentEraserSize;
            Rectangle eraseRect = new Rectangle(point.x - size/2, point.y - size/2, size, size);

            List<Shape> newShapes = new ArrayList<>();
            for (Shape shape : shapes) {
                if (!shape.getBounds().intersects(eraseRect)) {
                    newShapes.add(shape);
                }
            }

            shapes = newShapes;
            saveHistory(); // 保存历史记录
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (originalImage != null) {
                g.drawImage(originalImage, 0, 0, this);
            }

            for (Shape shape : shapes) {
                shape.draw((Graphics2D) g);
            }
        }
    }
}

// 钉图窗口类
class PinnedWindow extends JFrame {
    private BufferedImage originalImage;
    private BufferedImage editedImage;
    private BufferedImage scaledImage; // 声明scaledImage
    private JLabel imageLabel;
    private boolean isMaximized = false;
    private Dimension originalSize;
    private Point startDrag = null;
    private double scale = 1.0;
    private final double MIN_SCALE = 0.25;
    private final double MAX_SCALE = 5.0;

    // 编辑相关变量
    private List<Shape> shapes = new ArrayList<>();
    private Shape currentShape = null;
    private String currentTool = "rectangle";
    private Color currentColor = Color.RED;
    private int currentStroke = 2;
    private int currentEraserSize = 20; // 橡皮擦大小
    private boolean isEditing = false;
    private JToolBar editToolbar;

    // 历史记录用于撤销
    private Stack<List<Shape>> historyStack = new Stack<>();
    private static final int MAX_HISTORY = 100;

    public PinnedWindow(BufferedImage image) {
        this.originalImage = image;
        this.editedImage = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        Graphics2D g2d = editedImage.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        this.scaledImage = editedImage; // 初始化scaledImage
        initComponents();
    }

    private void initComponents() {
        setTitle("钉图");
        setUndecorated(true);
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));

        // 创建标题栏
        JPanel titleBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        titleBar.setBackground(new Color(0, 0, 0, 100));

        JButton closeButton = new JButton("×");
        closeButton.setForeground(Color.WHITE);
        closeButton.setBackground(Color.RED);
        closeButton.setBorderPainted(false);
        closeButton.addActionListener(e -> dispose());

        JButton minimizeButton = new JButton("−");
        minimizeButton.setForeground(Color.WHITE);
        minimizeButton.setBackground(new Color(50, 50, 50));
        minimizeButton.setBorderPainted(false);
        minimizeButton.addActionListener(e -> setExtendedState(JFrame.ICONIFIED));

        JButton maximizeButton = new JButton("□");
        maximizeButton.setForeground(Color.WHITE);
        maximizeButton.setBackground(new Color(50, 50, 50));
        maximizeButton.setBorderPainted(false);
        maximizeButton.addActionListener(e -> toggleMaximize());

        // 编辑按钮
        JButton editButton = new JButton("编辑");
        editButton.addActionListener(e -> toggleEditing());

        titleBar.add(editButton);
        titleBar.add(minimizeButton);
        titleBar.add(maximizeButton);
        titleBar.add(closeButton);

        // 创建图像面板
        imageLabel = new JLabel(new ImageIcon(scaledImage));
        JScrollPane scrollPane = new JScrollPane(imageLabel);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));

        // 添加鼠标监听器用于编辑
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!isEditing) return;

                startDrag = e.getPoint();
                Point imagePoint = convertToImageCoordinates(e.getPoint());

                if (currentTool.equals("text")) {
                    createTextField(imagePoint);
                } else if (currentTool.equals("eraser")) {
                    eraseShapes(imagePoint);
                } else {
                    saveHistory(); // 保存历史记录
                    switch (currentTool) {
                        case "rectangle":
                            currentShape = new RectangleShape(imagePoint, currentColor, currentStroke);
                            break;
                        case "circle":
                            currentShape = new CircleShape(imagePoint, currentColor, currentStroke);
                            break;
                        case "line":
                            currentShape = new LineShape(imagePoint, currentColor, currentStroke);
                            break;
                        case "arrow":
                            currentShape = new ArrowShape(imagePoint, currentColor, currentStroke);
                            break;
                    }
                    addShape(currentShape);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!isEditing || currentShape == null) return;

                Point imagePoint = convertToImageCoordinates(e.getPoint());
                currentShape.setEndPoint(imagePoint);
                currentShape = null;
                updateImage();
            }
        });

        imageLabel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!isEditing || currentShape == null) return;

                Point imagePoint = convertToImageCoordinates(e.getPoint());
                currentShape.setEndPoint(imagePoint);
                updateImage();
            }
        });

        // 添加滚轮事件处理
        scrollPane.getViewport().addMouseWheelListener(e -> {
            if (!isEditing) {
                handleZoom(e);
            }
        });

        // 设置布局
        setLayout(new BorderLayout());
        add(titleBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // 调整大小
        pack();

        // 添加拖动功能
        titleBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                startDrag = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                startDrag = null;
            }
        });

        titleBar.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (startDrag != null && !isEditing) {
                    Point location = getLocation();
                    setLocation(location.x + e.getX() - startDrag.x,
                            location.y + e.getY() - startDrag.y);
                }
            }
        });

        // 双击标题栏最大化/还原
        titleBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !isEditing) {
                    toggleMaximize();
                }
            }
        });

        // 创建编辑工具栏
        createEditToolbar();

        // 添加快捷键支持
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z) {
                    undo();
                }
            }
        });
    }

    private void createEditToolbar() {
        editToolbar = new JToolBar();
        editToolbar.setFloatable(false);
        editToolbar.setVisible(false);

        JButton undoButton = new JButton("撤销");
        undoButton.addActionListener(e -> undo());

        JButton rectangleButton = new JButton("矩形");
        rectangleButton.addActionListener(e -> setCurrentTool("rectangle"));

        JButton circleButton = new JButton("圆形");
        circleButton.addActionListener(e -> setCurrentTool("circle"));

        JButton lineButton = new JButton("直线");
        lineButton.addActionListener(e -> setCurrentTool("line"));

        JButton arrowButton = new JButton("箭头");
        arrowButton.addActionListener(e -> setCurrentTool("arrow"));

        JButton textButton = new JButton("文字");
        textButton.addActionListener(e -> setCurrentTool("text"));

        JButton eraserButton = new JButton("橡皮擦");
        eraserButton.addActionListener(e -> setCurrentTool("eraser"));

        String[] colors = {"红色", "绿色", "蓝色", "黄色", "黑色"};
        JComboBox<String> colorComboBox = new JComboBox<>(colors);
        colorComboBox.addActionListener(e -> {
            String colorName = (String) colorComboBox.getSelectedItem();
            switch (colorName) {
                case "红色":
                    currentColor = Color.RED;
                    break;
                case "绿色":
                    currentColor = Color.GREEN;
                    break;
                case "蓝色":
                    currentColor = Color.BLUE;
                    break;
                case "黄色":
                    currentColor = Color.YELLOW;
                    break;
                case "黑色":
                    currentColor = Color.BLACK;
                    break;
            }
        });

        JSpinner strokeSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 10, 1));
        strokeSpinner.addChangeListener(e -> currentStroke = (int) strokeSpinner.getValue());

        JSpinner eraserSizeSpinner = new JSpinner(new SpinnerNumberModel(20, 10, 100, 10));
        eraserSizeSpinner.addChangeListener(e -> currentEraserSize = (int) eraserSizeSpinner.getValue());

        editToolbar.add(undoButton);
        editToolbar.addSeparator();
        editToolbar.add(rectangleButton);
        editToolbar.add(circleButton);
        editToolbar.add(lineButton);
        editToolbar.add(arrowButton);
        editToolbar.add(textButton);
        editToolbar.add(eraserButton);
        editToolbar.addSeparator();
        editToolbar.add(new JLabel("颜色:"));
        editToolbar.add(colorComboBox);
        editToolbar.add(new JLabel(" 粗细:"));
        editToolbar.add(strokeSpinner);
        editToolbar.addSeparator();
        editToolbar.add(new JLabel("橡皮擦大小:"));
        editToolbar.add(eraserSizeSpinner);

        add(editToolbar, BorderLayout.SOUTH);
    }

    private void toggleEditing() {
        isEditing = !isEditing;
        editToolbar.setVisible(isEditing);

        if (isEditing) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
            currentShape = null;
        }
    }

    private void setCurrentTool(String tool) {
        this.currentTool = tool;
    }

    private void addShape(Shape shape) {
        shapes.add(shape);
    }

    private void saveHistory() {
        if (historyStack.size() >= MAX_HISTORY) {
            historyStack.remove(0);
        }
        historyStack.push(new ArrayList<>(shapes));
    }

    private void undo() {
        if (!historyStack.isEmpty()) {
            shapes = historyStack.pop();
            updateImage();
        }
    }

    private void createTextField(Point point) {
        JTextField textField = new JTextField();
        textField.setForeground(currentColor);
        textField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        textField.setBorder(BorderFactory.createLineBorder(currentColor));
        textField.setOpaque(false);
        textField.setSize(100, 25);

        // 转换坐标为组件坐标
        Point componentPoint = convertToComponentCoordinates(point);
        textField.setLocation(componentPoint);

        imageLabel.add(textField);

        textField.addActionListener(e -> {
            String text = textField.getText();
            if (!text.isEmpty()) {
                addShape(new TextShape(point, text, currentColor, 14));
                saveHistory(); // 保存历史记录
            }
            imageLabel.remove(textField);
            updateImage();
        });

        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String text = textField.getText();
                if (!text.isEmpty()) {
                    addShape(new TextShape(point, text, currentColor, 14));
                    saveHistory(); // 保存历史记录
                }
                imageLabel.remove(textField);
                updateImage();
            }
        });

        textField.requestFocus();
        imageLabel.repaint();
    }

    private void eraseShapes(Point point) {
        int size = currentEraserSize;
        Rectangle eraseRect = new Rectangle(point.x - size/2, point.y - size/2, size, size);

        List<Shape> newShapes = new ArrayList<>();
        for (Shape shape : shapes) {
            if (!shape.getBounds().intersects(eraseRect)) {
                newShapes.add(shape);
            }
        }

        shapes = newShapes;
        saveHistory(); // 保存历史记录
        updateImage();
    }

    private Point convertToImageCoordinates(Point componentPoint) {
        JViewport viewport = (JViewport) imageLabel.getParent();
        Point viewPosition = viewport.getViewPosition();

        double scaleX = (double) originalImage.getWidth() / imageLabel.getWidth();
        double scaleY = (double) originalImage.getHeight() / imageLabel.getHeight();

        return new Point(
                (int) ((componentPoint.x + viewPosition.x) * scaleX),
                (int) ((componentPoint.y + viewPosition.y) * scaleY)
        );
    }

    private Point convertToComponentCoordinates(Point imagePoint) {
        JViewport viewport = (JViewport) imageLabel.getParent();
        Point viewPosition = viewport.getViewPosition();

        double scaleX = (double) imageLabel.getWidth() / originalImage.getWidth();
        double scaleY = (double) imageLabel.getHeight() / originalImage.getHeight();

        return new Point(
                (int) (imagePoint.x * scaleX - viewPosition.x),
                (int) (imagePoint.y * scaleY - viewPosition.y)
        );
    }

    private void updateImage() {
        Graphics2D g2d = editedImage.createGraphics();
        g2d.drawImage(originalImage, 0, 0, null);

        for (Shape shape : shapes) {
            shape.draw(g2d);
        }

        g2d.dispose();

        // 更新缩放后的图像
        int newWidth = (int) (editedImage.getWidth() * scale);
        int newHeight = (int) (editedImage.getHeight() * scale);

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, editedImage.getType());
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(editedImage, 0, 0, newWidth, newHeight, null);
        g.dispose();

        scaledImage = scaled;
        imageLabel.setIcon(new ImageIcon(scaledImage));
    }

    private void handleZoom(MouseWheelEvent e) {
        int wheelRotation = e.getWheelRotation();
        double zoomFactor = wheelRotation < 0 ? 1.15 : 0.85;

        scale *= zoomFactor;
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));

        int newWidth = (int) (originalImage.getWidth() * scale);
        int newHeight = (int) (originalImage.getHeight() * scale);

        BufferedImage newImage = new BufferedImage(newWidth, newHeight, originalImage.getType());
        Graphics2D g2d = newImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        scaledImage = newImage;
        imageLabel.setIcon(new ImageIcon(scaledImage));
        pack();
    }

    private void toggleMaximize() {
        if (isMaximized) {
            // 还原
            setSize(originalSize);
            setLocationRelativeTo(null);
        } else {
            // 保存原始大小
            originalSize = getSize();

            // 最大化
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            setSize(screenSize);
            setLocation(0, 0);
        }
        isMaximized = !isMaximized;
    }
}

// 形状接口
interface Shape {
    void draw(Graphics2D g2d);
    void setEndPoint(Point endPoint);
    Rectangle getBounds(); // 获取形状边界
}

// 矩形形状类
class RectangleShape implements Shape {
    private Point startPoint;
    private Point endPoint;
    private Color color;
    private int stroke;

    public RectangleShape(Point startPoint, Color color, int stroke) {
        this.startPoint = startPoint;
        this.endPoint = startPoint;
        this.color = color;
        this.stroke = stroke;
    }

    @Override
    public void draw(Graphics2D g2d) {
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(stroke));

        int x = Math.min(startPoint.x, endPoint.x);
        int y = Math.min(startPoint.y, endPoint.y);
        int width = Math.abs(endPoint.x - startPoint.x);
        int height = Math.abs(endPoint.y - startPoint.y);

        g2d.drawRect(x, y, width, height);
    }

    @Override
    public void setEndPoint(Point endPoint) {
        this.endPoint = endPoint;
    }

    @Override
    public Rectangle getBounds() {
        int x = Math.min(startPoint.x, endPoint.x);
        int y = Math.min(startPoint.y, endPoint.y);
        int width = Math.abs(endPoint.x - startPoint.x);
        int height = Math.abs(endPoint.y - startPoint.y);
        return new Rectangle(x, y, width, height);
    }
}

// 圆形形状类
class CircleShape implements Shape {
    private Point startPoint;
    private Point endPoint;
    private Color color;
    private int stroke;

    public CircleShape(Point startPoint, Color color, int stroke) {
        this.startPoint = startPoint;
        this.endPoint = startPoint;
        this.color = color;
        this.stroke = stroke;
    }

    @Override
    public void draw(Graphics2D g2d) {
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(stroke));

        int x = Math.min(startPoint.x, endPoint.x);
        int y = Math.min(startPoint.y, endPoint.y);
        int width = Math.abs(endPoint.x - startPoint.x);
        int height = Math.abs(endPoint.y - startPoint.y);

        g2d.drawOval(x, y, width, height);
    }

    @Override
    public void setEndPoint(Point endPoint) {
        this.endPoint = endPoint;
    }

    @Override
    public Rectangle getBounds() {
        int x = Math.min(startPoint.x, endPoint.x);
        int y = Math.min(startPoint.y, endPoint.y);
        int width = Math.abs(endPoint.x - startPoint.x);
        int height = Math.abs(endPoint.y - startPoint.y);
        return new Rectangle(x, y, width, height);
    }
}

// 直线形状类
class LineShape implements Shape {
    private Point startPoint;
    private Point endPoint;
    private Color color;
    private int stroke;

    public LineShape(Point startPoint, Color color, int stroke) {
        this.startPoint = startPoint;
        this.endPoint = startPoint;
        this.color = color;
        this.stroke = stroke;
    }

    @Override
    public void draw(Graphics2D g2d) {
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(stroke));
        g2d.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y);
    }

    @Override
    public void setEndPoint(Point endPoint) {
        this.endPoint = endPoint;
    }

    @Override
    public Rectangle getBounds() {
        int minX = Math.min(startPoint.x, endPoint.x);
        int minY = Math.min(startPoint.y, endPoint.y);
        int maxX = Math.max(startPoint.x, endPoint.x);
        int maxY = Math.max(startPoint.y, endPoint.y);
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }
}

// 箭头形状类
class ArrowShape implements Shape {
    private Point startPoint;
    private Point endPoint;
    private Color color;
    private int stroke;

    public ArrowShape(Point startPoint, Color color, int stroke) {
        this.startPoint = startPoint;
        this.endPoint = startPoint;
        this.color = color;
        this.stroke = stroke;
    }

    @Override
    public void draw(Graphics2D g2d) {
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(stroke));

        // 绘制直线
        g2d.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y);

        // 绘制箭头头部
        double angle = Math.atan2(endPoint.y - startPoint.y, endPoint.x - startPoint.x);
        int headLength = 10;

        int arrowX1 = endPoint.x - (int) (headLength * Math.cos(angle - Math.PI / 6));
        int arrowY1 = endPoint.y - (int) (headLength * Math.sin(angle - Math.PI / 6));
        int arrowX2 = endPoint.x - (int) (headLength * Math.cos(angle + Math.PI / 6));
        int arrowY2 = endPoint.y - (int) (headLength * Math.sin(angle + Math.PI / 6));

        g2d.drawLine(endPoint.x, endPoint.y, arrowX1, arrowY1);
        g2d.drawLine(endPoint.x, endPoint.y, arrowX2, arrowY2);
    }

    @Override
    public void setEndPoint(Point endPoint) {
        this.endPoint = endPoint;
    }

    @Override
    public Rectangle getBounds() {
        int minX = Math.min(startPoint.x, endPoint.x);
        int minY = Math.min(startPoint.y, endPoint.y);
        int maxX = Math.max(startPoint.x, endPoint.x);
        int maxY = Math.max(startPoint.y, endPoint.y);

        // 扩展边界以包含箭头头部
        double angle = Math.atan2(endPoint.y - startPoint.y, endPoint.x - startPoint.x);
        int headLength = 10;
        int arrowX1 = endPoint.x - (int) (headLength * Math.cos(angle - Math.PI / 6));
        int arrowY1 = endPoint.y - (int) (headLength * Math.sin(angle - Math.PI / 6));
        int arrowX2 = endPoint.x - (int) (headLength * Math.cos(angle + Math.PI / 6));
        int arrowY2 = endPoint.y - (int) (headLength * Math.sin(angle + Math.PI / 6));

        minX = Math.min(minX, Math.min(arrowX1, arrowX2));
        minY = Math.min(minY, Math.min(arrowY1, arrowY2));
        maxX = Math.max(maxX, Math.max(arrowX1, arrowX2));
        maxY = Math.max(maxY, Math.max(arrowY1, arrowY2));

        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }
}

// 文字形状类
class TextShape implements Shape {
    private Point position;
    private String text;
    private Color color;
    private int fontSize;

    public TextShape(Point position, String text, Color color, int fontSize) {
        this.position = position;
        this.text = text;
        this.color = color;
        this.fontSize = fontSize;
    }

    @Override
    public void draw(Graphics2D g2d) {
        g2d.setColor(color);
        Font font = new Font("微软雅黑", Font.PLAIN, fontSize);
        g2d.setFont(font);

        FontMetrics metrics = g2d.getFontMetrics(font);
        int baseline = metrics.getAscent(); // 获取文字基线

        g2d.drawString(text, position.x, position.y + baseline);
    }

    @Override
    public void setEndPoint(Point endPoint) {
        // 文字不需要终点
    }

    @Override
    public Rectangle getBounds() {
        Font font = new Font("微软雅黑", Font.PLAIN, fontSize);
        FontMetrics metrics = Toolkit.getDefaultToolkit().getFontMetrics(font);
        int width = metrics.stringWidth(text);
        int height = metrics.getHeight();
        return new Rectangle(position.x, position.y - metrics.getDescent(), width, height);
    }
}