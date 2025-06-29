//package com.tyler.recorder2;
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//import java.awt.event.WindowAdapter;
//import java.awt.event.WindowEvent;
//import java.io.File;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//
//import org.bytedeco.javacv.*;
//import org.bytedeco.opencv.opencv_core.Mat;
//import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
//import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGRA2BGR;
//
//public class ScreenRecorder {
//    private static final int FRAME_RATE = 20;
//    private static final int AUDIO_SAMPLE_RATE = 44100;
//    private static final int AUDIO_SAMPLE_SIZE_IN_BITS = 16;
//    private static final int AUDIO_CHANNELS = 2;
//    private static final boolean AUDIO_SIGNED = true;
//    private static final boolean AUDIO_BIG_ENDIAN = false;
//
//    private JFrame frame;
//    private JButton startButton, stopButton, pauseButton;
//    private JLabel statusLabel;
//    private Robot robot;
//    private Rectangle screenRect;
//    private Thread recordingThread;
//    private boolean isRecording = false;
//    private boolean isPaused = false;
//    private File outputFile;
//    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
//    private FFmpegFrameRecorder recorder;
//    private Java2DFrameConverter converter;
//    private long startTime = 0;
//    private long pausedTime = 0;
//
//    public ScreenRecorder() {
//        try {
//            robot = new Robot();
//        } catch (AWTException e) {
//            e.printStackTrace();
//            JOptionPane.showMessageDialog(null, "无法初始化屏幕录制器: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
//            System.exit(1);
//        }
//
//        screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
//        initUI();
//    }
//
//    private void initUI() {
//        frame = new JFrame("屏幕录制软件");
//        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
//        frame.addWindowListener(new WindowAdapter() {
//            @Override
//            public void windowClosing(WindowEvent e) {
//                if (isRecording) {
//                    int result = JOptionPane.showConfirmDialog(frame, "正在录制中，是否停止录制并退出?", "确认退出", JOptionPane.YES_NO_OPTION);
//                    if (result == JOptionPane.YES_OPTION) {
//                        stopRecording();
//                        frame.dispose();
//                    }
//                } else {
//                    frame.dispose();
//                }
//            }
//        });
//
//        startButton = new JButton("开始录制");
//        stopButton = new JButton("停止录制");
//        pauseButton = new JButton("暂停录制");
//        statusLabel = new JLabel("准备就绪");
//
//        stopButton.setEnabled(false);
//        pauseButton.setEnabled(false);
//
//        startButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                startRecording();
//            }
//        });
//
//        stopButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                stopRecording();
//            }
//        });
//
//        pauseButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                if (isPaused) {
//                    resumeRecording();
//                } else {
//                    pauseRecording();
//                }
//            }
//        });
//
//        JPanel buttonPanel = new JPanel();
//        buttonPanel.add(startButton);
//        buttonPanel.add(stopButton);
//        buttonPanel.add(pauseButton);
//
//        frame.getContentPane().add(buttonPanel, BorderLayout.CENTER);
//        frame.getContentPane().add(statusLabel, BorderLayout.SOUTH);
//
//        frame.pack();
//        frame.setLocationRelativeTo(null);
//        frame.setVisible(true);
//    }
//
//    private void startRecording() {
//        try {
//            startButton.setEnabled(false);
//            stopButton.setEnabled(true);
//            pauseButton.setEnabled(true);
//            pauseButton.setText("暂停录制");
//            statusLabel.setText("正在录制...");
//
//            outputFile = new File("ScreenRecording_" + dateFormat.format(new Date()) + ".mp4");
//
//            // 初始化录制器
//            recorder = new FFmpegFrameRecorder(outputFile, screenRect.width, screenRect.height);
//            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
//            recorder.setFormat("mp4");
//            recorder.setFrameRate(FRAME_RATE);
//            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
//            recorder.setVideoOption("preset", "ultrafast");
//            recorder.setVideoOption("tune", "zerolatency");
//
//            // 音频设置 - 暂时禁用，简化问题
//            // recorder.setAudioChannels(AUDIO_CHANNELS);
//            // recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
//            // recorder.setSampleRate(AUDIO_SAMPLE_RATE);
//
//            recorder.start();
//
//            converter = new Java2DFrameConverter();
//            isRecording = true;
//            isPaused = false;
//            startTime = System.currentTimeMillis();
//            pausedTime = 0;
//
//            // 开始录制线程
//            recordingThread = new Thread(new RecordingRunnable());
//            recordingThread.start();
//        } catch (Exception ex) {
//            ex.printStackTrace();
//            JOptionPane.showMessageDialog(frame, "无法开始录制: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
//            resetUI();
//        }
//    }
//
//    private void pauseRecording() {
//        if (isPaused) return;
//
//        isPaused = true;
//        pauseButton.setText("继续录制");
//        statusLabel.setText("已暂停");
//        pausedTime = System.currentTimeMillis();
//    }
//
//    private void resumeRecording() {
//        if (!isPaused) return;
//
//        // 计算暂停时间并调整开始时间
//        startTime += (System.currentTimeMillis() - pausedTime);
//        isPaused = false;
//        pauseButton.setText("暂停录制");
//        statusLabel.setText("正在录制...");
//    }
//
//    private void stopRecording() {
//        isRecording = false;
//        try {
//            if (recordingThread != null && recordingThread.isAlive()) {
//                recordingThread.join();
//            }
//
//            if (recorder != null) {
//                recorder.stop();
//                recorder.release();
//                recorder = null;
//            }
//        } catch (InterruptedException | FrameRecorder.Exception e) {
//            e.printStackTrace();
//        }
//
//        resetUI();
//
//        if (outputFile.exists()) {
//            JOptionPane.showMessageDialog(frame, "录制完成\n文件保存至: " + outputFile.getAbsolutePath(), "录制完成", JOptionPane.INFORMATION_MESSAGE);
//        } else {
//            JOptionPane.showMessageDialog(frame, "录制失败，未生成视频文件", "错误", JOptionPane.ERROR_MESSAGE);
//        }
//    }
//
//    private void resetUI() {
//        startButton.setEnabled(true);
//        stopButton.setEnabled(false);
//        pauseButton.setEnabled(false);
//        statusLabel.setText("准备就绪");
//    }
//
//    private class RecordingRunnable implements Runnable {
//        @Override
//        public void run() {
//            try {
//                while (isRecording) {
//                    if (!isPaused) {
//                        // 捕获屏幕
//                        BufferedImage image = robot.createScreenCapture(screenRect);
//
//                        // 转换为Frame并录制
//                        Frame frame = converter.convert(image);
//                        long timestamp = (System.currentTimeMillis() - startTime) * 1000;
//                        if (timestamp > recorder.getTimestamp()) {
//                            recorder.setTimestamp(timestamp);
//                        }
//                        recorder.record(frame);
//                    }
//
//                    // 控制帧率
//                    Thread.sleep(1000 / FRAME_RATE);
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//                JOptionPane.showMessageDialog(frame, "录制过程中发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
//            }
//        }
//    }
//
//    public static void main(String[] args) {
//        // 确保JavaCV库正确加载
//        System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0");
//        System.setProperty("org.bytedeco.javacpp.maxbytes", "0");
//
//        SwingUtilities.invokeLater(new Runnable() {
//            @Override
//            public void run() {
//                new ScreenRecorder();
//            }
//        });
//    }
//}