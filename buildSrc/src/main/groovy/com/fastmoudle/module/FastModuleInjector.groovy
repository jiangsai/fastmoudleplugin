package com.fastmoudle.module

import org.apache.commons.io.IOUtils
import org.objectweb.asm.*
import org.objectweb.asm.commons.AdviceAdapter

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import com.fastmoudle.utils.Logger

class FastModuleInjector {

    List<String> proxyModuleClassList

    FastModuleInjector(List<String> list) {
        proxyModuleClassList = list
    }

    void execute() {
        System.out.println("开始执行ASM方法======>>>>>>>>")
        Logger.logd("开始执行ASM方法======>>>>>>>>")

        File srcFile = ScanUtil.FILE_CONTAINS_INIT_CLASS
        //创建一个临时jar文件，要修改注入的字节码会先写入该文件里
        def optJar = new File(srcFile.getParent(), srcFile.name + ".opt")
        if (optJar.exists())
            optJar.delete()
        def file = new JarFile(srcFile)
        Enumeration<JarEntry> enumeration = file.entries()
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(optJar))
        while (enumeration.hasMoreElements()) {
            JarEntry jarEntry = enumeration.nextElement()
            String entryName = jarEntry.getName()
            ZipEntry zipEntry = new ZipEntry(entryName)
            InputStream inputStream = file.getInputStream(jarEntry)
            jarOutputStream.putNextEntry(zipEntry)

            //找到需要插入代码的class，通过ASM动态注入字节码
            if (ScanUtil.REGISTER_CLASS_FILE_NAME == entryName) {
                Logger.logd("insert register code to class >> " + entryName)

                ClassReader classReader = new ClassReader(inputStream)
                // 构建一个ClassWriter对象，并设置让系统自动计算栈和本地变量大小
                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
                ClassVisitor classVisitor = new FastModuleClassVisitor(classWriter)
                //开始扫描class文件
                classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)

                byte[] bytes = classWriter.toByteArray()
                //将注入过字节码的class，写入临时jar文件里
                jarOutputStream.write(bytes)
            } else {
                //不需要修改的class，原样写入临时jar文件里
                jarOutputStream.write(IOUtils.toByteArray(inputStream))
            }
            inputStream.close()
            jarOutputStream.closeEntry()
        }

        jarOutputStream.close()
        file.close()

        //删除原来的jar文件
        if (srcFile.exists()) {
            srcFile.delete()
        }
        //重新命名临时jar文件，新的jar包里已经包含了我们注入的字节码了
        optJar.renameTo(srcFile)
    }

    class FastModuleClassVisitor extends ClassVisitor {
        FastModuleClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM7, classVisitor)
        }

        @Override
        MethodVisitor visitMethod(int access, String name,
                                  String desc, String signature,
                                  String[] exception) {
            Logger.logd("visit method: " + name)
            System.out.println("开始执行FastModuleClassVisitor方法======>>>>>>>>"+name)
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exception)
            //找到 ModuleLoader里的loadModules()方法
            if ("loadModules" == name) {
                mv = new LoadModuleMethodAdapter(mv, access, name, desc)
            }
            return mv
        }
    }

    class LoadModuleMethodAdapter extends AdviceAdapter {

        LoadModuleMethodAdapter(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM7, mv, access, name, desc)
        }

        @Override
        protected void onMethodEnter() {
            super.onMethodEnter()
            System.out.println("开始执行LoadModuleMethodAdapter方法======>>>>>>>>"+proxyModuleClassList.size())
           // System.out.println("开始执行LoadModuleMethodAdapter方法======>>>>>>>>"+proxyModuleClassList.size())
            Logger.log()
            proxyModuleClassList.forEach({ proxyClassName ->
                Logger.logd("开始注入代码：${proxyClassName}")
                def fullName = ScanUtil.PROXY_CLASS_PACKAGE_NAME.replace("/", ".") + "." + proxyClassName.substring(0, proxyClassName.length() - 6)
                Logger.logd("full classname = ${fullName}")
                mv.visitLdcInsn(fullName)
                mv.visitMethodInsn(INVOKESTATIC, "com/fastmoudle/fastmoudle_interfaces/module/ModuleLoader", "registerModule", "(Ljava/lang/String;)V", false)
            })
        }

        @Override
        protected void onMethodExit(int opcode) {
            super.onMethodExit(opcode)
            Logger.log()
        }
    }

}