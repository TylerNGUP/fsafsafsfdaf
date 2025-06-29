
#### 如何将 ScreenshotTool.java 程序打包成可执行的 JAR 文件，并创建批处理文件来方便运行。以下是具体步骤：
位置：(src/main/java/com/tyler/screenshot/ScreenshotTool.java)

## 1. 打包 Java 程序为可执行 JAR 文件
   首先需要确保你的 Java 项目已经正确配置了 Main 类和清单文件（Manifest）。

### 1.1 创建清单文件（Manifest）
   在项目根目录下创建一个名为manifest.mf的文件，内容如下：

```plaintext
Main-Class: com.tyler.screenshot.ScreenshotTool

```
注意：文件末尾需要有一个空行，这是 JAR 规范要求的。

### 1.2 使用命令行打包 JAR 文件
打开终端，进入项目根目录，执行以下命令：

```bash
# 进入ScreenshotTool.java的根目录，编译Java文件
javac -encoding UTF-8 -d bin *.java

# 创建JAR文件
jar cfm ScreenshotTool.jar manifest.mf -C bin .
```

### 2. 创建批处理文件（.bat）
批处理文件可以方便地启动 Java 程序，创建一个名为screenshot.bat的文件，内容如下：

```batch
@echo off
java -jar "C:\路径\到\你的\ScreenshotTool.jar"
pause
```

将上面的路径替换为你实际存放 JAR 文件的路径。如果你想双击后窗口自动关闭，可以去掉pause命令。

### 3. 自定义快捷方式图标（可选）
如果你想为快捷方式设置自定义图标，可以按照以下步骤操作：

右键点击快捷方式，选择 "属性"
在 "快捷方式" 选项卡中，点击 "更改图标" 按钮
浏览到包含图标的文件（.ico 格式），选择后点击 "确定"
