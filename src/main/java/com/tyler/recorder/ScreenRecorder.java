package com.tyler.recorder;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ScreenRecorder {
    private static final String OUTPUT_FILE = "screen_record.mp4";
    private static final int FRAME_RATE = 20;
    private static final int RECORD_TIME = 10; // 录制时间(秒)

    public static void main(String[] args) throws Exception {
        // 1. 获取屏幕尺寸
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = screenSize.width;
        int height = screenSize.height;

        // 2. 创建FFmpeg录屏器
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(
            OUTPUT_FILE, width, height);
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat("mp4");
        recorder.setFrameRate(FRAME_RATE);
        recorder.setVideoBitrate(2000000); // 2 Mbps

        try {
            // 3. 开始录制
            recorder.start();
            Robot robot = new Robot();
            long startTime = System.currentTimeMillis();

            // 4. 录制循环
            while (System.currentTimeMillis() - startTime < RECORD_TIME * 3000) {
                // 捕获屏幕
                BufferedImage screenCapture = robot.createScreenCapture(
                    new Rectangle(0, 0, width, height));

                // 转换为帧并录制
                Frame frame = Java2DFrameUtils.toFrame(screenCapture);
                recorder.record(frame);

                // 控制帧率
                Thread.sleep(1000 / FRAME_RATE);
            }
        } finally {
            // 5. 停止录制
            recorder.stop();
            recorder.release();
            System.out.println("录制完成! 文件保存至: " + OUTPUT_FILE);
        }
    }
}