package com.knowledge.mnlin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.EditorTextField;

import java.awt.Color;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Created on 2019/3/19  21:03
 * function :
 *
 * @author mnlin
 */
public class KViewBind extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        //当前文件/当前光标位置
        PsiFile psiFile = e.getData(DataKeys.PSI_FILE);
        Editor editor = e.getData(DataKeys.EDITOR);

        //如果对象不为空,可去获取资源文件(布局文件)
        if (editor != null && psiFile != null) {
            CaretModel caret = editor.getCaretModel();
            PsiElement item = psiFile.findElementAt(caret.getOffset());
            PsiElement parent = item.getParent().getParent();
            String regex = "^R\\.layout\\.(\\w+)$";
            if (parent != null && parent.getText().matches(regex)) {
                String layoutFile = parent.getText().replaceFirst(regex, "$1");
                System.out.println("需要处理的布局文件为:" + layoutFile);

                //获取文件
                if (e.getProject() != null) {
                    PsiFile[] filesByName = FilenameIndex.getFilesByName(e.getProject(), layoutFile + ".xml", GlobalSearchScope.projectScope(e.getProject()));

                    //如果文件能找到
                    if (filesByName.length == 1) {
                        try {
                            //文件内容
                            PsiFile file = filesByName[0];
                            System.out.println(file.getText());

                            //xml解析,获取所有标签元素(不处理include标签)
                            List<Pair<String, String>> attributeValues = new LinkedList<>();
                            file.accept(new XmlRecursiveElementVisitor(true) {
                                @Override
                                public void visitXmlTag(XmlTag tag) {
                                    super.visitXmlTag(tag);
                                    //获取标签以及value值
                                    String value = tag.getAttributeValue("android:id");
                                    String regex = "^@\\+id/(.*)$";
                                    if (value != null && value.matches(regex)) {
                                        attributeValues.add(new Pair<>(tag.getName(), value.replaceFirst(regex, "$1")));
                                    }
                                }
                            });

                            //获取TextView的class
                            PsiClass class_textView = JavaPsiFacade.getInstance(psiFile.getProject()).findClass("android.widget.TextView", GlobalSearchScope.allScope(psiFile.getProject()));
                            StringBuilder block = new StringBuilder();
                            for (Pair<String, String> keyValue : attributeValues) {
                                //标签所在view类型
                                String tagClassName;
                                if (!keyValue.first.contains(".")) {
                                    //如果包含 . ,说明不是系统原生的对象view,因此默认加上 android.widget 前缀
                                    tagClassName = "android.widget." + keyValue.first;
                                } else {
                                    tagClassName = keyValue.first;
                                }

                                //每个标签对应的class
                                PsiClass class_tag = JavaPsiFacade.getInstance(psiFile.getProject()).findClass(tagClassName, GlobalSearchScope.allScope(psiFile.getProject()));

                                //如果是TextView/EditText类型的视图,才会添加元素进入
                                if (class_tag != null && class_textView != null && (class_tag.isInheritor(class_textView, false) || class_tag.equals(class_textView))) {
                                    block.append("private var ")
                                            .append(conventIdToParam(keyValue.second))
                                            .append(": String by viewBind(R.id.")
                                            .append(keyValue.second)
                                            .append(")\n");
                                }
                            }

                            //显示文字(弹出框形式)
                            showEditText(block.toString(), editor);
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    } else {
                        showDialog(editor, "无法找到该布局文件,或布局文件不唯一", 2);
                    }
                }
            }
        }
    }

    /**
     * 将已有id转换wi变量值
     */
    private String conventIdToParam(String id) {
        String old = id.replaceAll("^.+?_(.+)$", "$1");

        //如果长度 多短,格式不规范,则 不进行处理
        if (old.length() <= 1) {
            return id;
        }

        String reg = "_+\\w";
        Matcher matcher = Pattern.compile(reg).matcher(old);

        //针对每个匹配到的内容进行替换
        StringBuilder builder = new StringBuilder();
        int offset = 0;
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            builder.append(old.substring(offset, start))
                    .append(old.substring(end - 1, end).toUpperCase());
            offset = end;
        }
        builder.append(old.substring(offset, old.length()));
        String result = builder.toString().replaceAll("[^0-9a-zA-Z]", "");
        return result.substring(0, 1).toLowerCase() + result.substring(1, result.length());
    }

    /**
     * 显示提示框
     */
    private void showDialog(final Editor editor, final String result, final int time) {
        ApplicationManager.getApplication().invokeLater(() -> {
            JBPopupFactory factory = JBPopupFactory.getInstance();
            factory.createHtmlTextBalloonBuilder(result, null, Color.gray, null)
                    .setFadeoutTime(time * 1000)
                    .createBalloon()
                    .show(factory.guessBestPopupLocation(editor), Balloon.Position.below);
        });
    }


    /**
     * 显示文本框
     */
    private void showEditText(String result, final Editor editor) {
        JBPopupFactory instance = JBPopupFactory.getInstance();
        instance.createDialogBalloonBuilder(new EditorTextField(EditorFactory.getInstance().createDocument(result), null, FileTypes.PLAIN_TEXT, false, false), "KViewBind-Generate")
                .setHideOnKeyOutside(true)
                .setHideOnClickOutside(true)
                .createBalloon()
                .show(instance.guessBestPopupLocation(editor), Balloon.Position.below);
    }
}
