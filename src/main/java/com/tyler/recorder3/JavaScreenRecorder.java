package com.tyler.recorder3;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class JavaScreenRecorder {
    private static final int FPS = 15; // 帧率
    private static final int RECORD_SECONDS = 10; // 录制时长(秒)
    private static final String OUTPUT_DIR = "screen_record"; // 输出目录
    private static final String OUTPUT_VIDEO = "record.mjpeg.avi"; // 输出视频文件名

    public static void main(String[] args) {
        try {
            // 创建输出目录
            File outputDir = new File(OUTPUT_DIR);
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                System.err.println("无法创建输出目录: " + OUTPUT_DIR);
                return;
            }

            // 获取屏幕尺寸
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Rectangle screenRect = new Rectangle(screenSize);

            // 创建Robot对象
            Robot robot = new Robot();

            // 开始录制
            System.out.println("开始录制屏幕...");
            List<BufferedImage> frames = recordScreen(robot, screenRect);
            System.out.println("录制完成，共捕获 " + frames.size() + " 帧");

            // 将帧序列转换为视频
            System.out.println("开始生成视频文件...");
            createMJpegAVI(frames);
            System.out.println("视频生成完成: " + OUTPUT_VIDEO);

        } catch (AWTException e) {
            System.err.println("无法创建Robot对象: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("IO错误: " + e.getMessage());
        }
    }

    /**
     * 录制屏幕并返回帧序列
     */
    private static List<BufferedImage> recordScreen(Robot robot, Rectangle screenRect) {
        List<BufferedImage> frames = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        long frameInterval = 1000 / FPS; // 每帧间隔(毫秒)

        // 录制循环
        for (int frameCount = 0; ; frameCount++) {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startTime;

            // 检查是否达到录制时长
            if (elapsedTime >= RECORD_SECONDS * 1000) {
                break;
            }

            // 捕获屏幕
            BufferedImage frame = robot.createScreenCapture(screenRect);
            frames.add(frame);

            // 计算下一帧应等待的时间
            long nextFrameTime = startTime + (frameCount + 1) * frameInterval;
            long sleepTime = nextFrameTime - currentTime;

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return frames;
    }

    /**
     * 创建MJPEG格式的AVI文件
     */
    private static void createMJpegAVI(List<BufferedImage> frames) throws IOException {
        if (frames.isEmpty()) {
            return;
        }

        // 获取第一帧的尺寸
        BufferedImage firstFrame = frames.get(0);
        int width = firstFrame.getWidth();
        int height = firstFrame.getHeight();

        System.out.println("创建MJPEG AVI: " + width + "x" + height + " @" + FPS + "fps");

        try (FileOutputStream fos = new FileOutputStream(OUTPUT_VIDEO);
             DataOutputStream dos = new DataOutputStream(fos)) {

            // 1. 写入AVI文件头
            writeAviHeader(dos, frames.size(), width, height);

            // 2. 写入帧数据
            ByteArrayOutputStream indexBuffer = new ByteArrayOutputStream();
            DataOutputStream indexDos = new DataOutputStream(indexBuffer);
            int moviStartPos = 4; // "movi"列表开始位置

            // 写入'movi'列表
            dos.writeBytes("LIST");
            int moviListSizePos = dos.size(); // 记住位置稍后填充
            dos.writeInt(0); // 占位，稍后填充实际大小
            dos.writeBytes("movi");

            // 写入每帧数据
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage frame = frames.get(i);

                // 压缩为JPEG
                ByteArrayOutputStream jpegStream = new ByteArrayOutputStream();
                ImageIO.write(frame, "jpg", jpegStream);
                byte[] jpegData = jpegStream.toByteArray();

                // 写入帧头
                dos.writeBytes("00dc"); // 视频帧标识
                dos.writeInt(jpegData.length); // 帧大小
                dos.write(jpegData);

                // 添加到索引
                indexDos.writeBytes("00dc"); // 帧标识
                indexDos.writeInt(0x10);    // 标志（关键帧）
                indexDos.writeInt(moviStartPos); // 帧在movi列表中的偏移量
                indexDos.writeInt(jpegData.length); // 帧大小

                // 更新偏移量（8字节头 + 帧数据大小）
                moviStartPos += 8 + jpegData.length;
            }

            // 3. 写入索引
            dos.writeBytes("idx1");
            dos.writeInt(indexBuffer.size()); // 索引大小
            dos.write(indexBuffer.toByteArray());

            // 4. 更新文件大小信息
            updateFileSizes(dos, moviListSizePos);
        }
    }

    /**
     * 写入AVI文件头
     */
    private static void writeAviHeader(DataOutputStream dos, int frameCount, int width, int height) throws IOException {
        // RIFF头
        dos.writeBytes("RIFF");
        dos.writeInt(0); // 文件总大小，稍后填充
        dos.writeBytes("AVI ");

        // LIST头
        dos.writeBytes("LIST");
        dos.writeInt(192); // hdrl列表大小
        dos.writeBytes("hdrl");

        // avih主AVI头
        dos.writeBytes("avih");
        dos.writeInt(56); // 头大小
        dos.writeInt(1000000 / FPS); // 每帧微秒数
        dos.writeInt(0); // 最大字节/秒
        dos.writeInt(0); // 填充粒度
        dos.writeInt(0x10); // 标志（有索引）
        dos.writeInt(frameCount); // 总帧数
        dos.writeInt(0); // 初始帧
        dos.writeInt(1); // 流数量
        dos.writeInt(0); // 建议缓冲区大小
        dos.writeInt(width); // 宽度
        dos.writeInt(height); // 高度
        dos.writeInt(0); // 保留
        dos.writeInt(0); // 保留
        dos.writeInt(0); // 保留
        dos.writeInt(0); // 保留

        // LIST流头
        dos.writeBytes("LIST");
        dos.writeInt(116); // strl列表大小
        dos.writeBytes("strl");

        // strh流头
        dos.writeBytes("strh");
        dos.writeInt(56); // 头大小
        dos.writeBytes("vids"); // 视频流
        dos.writeBytes("MJPG"); // MJPEG格式
        dos.writeInt(0); // 标志
        dos.writeInt(0); // 优先级
        dos.writeInt(0); // 初始帧
        dos.writeInt(1); // 缩放
        dos.writeInt(FPS); // 速率
        dos.writeInt(0); // 开始时间
        dos.writeInt(frameCount); // 长度（帧数）
        dos.writeInt(0); // 建议缓冲区大小
        dos.writeInt(0); // 质量
        dos.writeInt(0); // 采样大小
        dos.writeShort(0); // 矩形左
        dos.writeShort(0); // 矩形上
        dos.writeShort(width); // 矩形右
        dos.writeShort(height); // 矩形下

        // strf流格式
        dos.writeBytes("strf");
        dos.writeInt(40); // BITMAPINFOHEADER大小
        dos.writeInt(width); // 宽度
        dos.writeInt(height); // 高度
        dos.writeShort(1); // 平面数
        dos.writeShort(24); // 位深度
        dos.writeBytes("MJPG"); // 压缩类型
        dos.writeInt(width * height * 3); // 图像大小（估计值）
        dos.writeInt(0); // 水平分辨率
        dos.writeInt(0); // 垂直分辨率
        dos.writeInt(0); // 使用颜色数
        dos.writeInt(0); // 重要颜色数
    }

    /**
     * 更新文件大小信息
     */
    private static void updateFileSizes(DataOutputStream dos, int moviListSizePos) throws IOException {
        // 获取当前文件大小
        int fileSize = dos.size();

        try (RandomAccessFile raf = new RandomAccessFile(OUTPUT_VIDEO, "rw")) {
            // 更新RIFF块大小（总文件大小 - 8）
            raf.seek(4);
            raf.writeInt(fileSize - 8);

            // 更新'movi'列表大小（从'movi'开始到文件结束 - 8）
            int moviListSize = fileSize - moviListSizePos - 8;
            raf.seek(moviListSizePos);
            raf.writeInt(moviListSize);
        }
    }

    /**
     * 备用方案：直接生成MJPEG文件（不是AVI，但几乎所有播放器都支持）
     */
    private static void createSimpleMJpeg(List<BufferedImage> frames) throws IOException {
        if (frames.isEmpty()) return;

        try (ImageOutputStream ios = new FileImageOutputStream(new File(OUTPUT_VIDEO))) {
            for (BufferedImage frame : frames) {
                ImageIO.write(frame, "jpg", ios);
            }
        }
        System.out.println("生成MJPEG视频文件: " + OUTPUT_VIDEO);
    }
}