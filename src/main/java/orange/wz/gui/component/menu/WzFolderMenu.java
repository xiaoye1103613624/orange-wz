package orange.wz.gui.component.menu;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.MainFrame;
import orange.wz.gui.component.FileDialog;
import orange.wz.gui.component.panel.EditPane;
import orange.wz.gui.utils.JMessageUtil;
import orange.wz.gui.utils.TreePathUtil;
import orange.wz.provider.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static orange.wz.gui.Icons.FiPackage;

@Slf4j
public final class WzFolderMenu extends TreeMenu {
    private final JTree tree;

    public WzFolderMenu(EditPane editPane, JTree tree) {
        super(editPane);
        this.tree = tree;

        JMenuItem btnPackage = new JMenuItem(MainFrame.i18n.get("test.temp0127"), FiPackage);
        btnPackage.addActionListener(e -> packageBtnAction());

        add(btnSave);
        add(btnPackage);
        add(btnUnload);
        add(btnReload);
        add(btnChangeKey);
        add(btnExport);
    }

    private void packageBtnAction() {
        TreePath[] selectedPaths = tree.getSelectionPaths();
        if (selectedPaths == null || selectedPaths.length == 0) return;

        Short fileVersion = null;
        while (fileVersion == null) {
            String input = JOptionPane.showInputDialog(MainFrame.i18n.get("test.temp0128"));
            if (input == null) return;
            try {
                short value = Short.parseShort(input.trim());
                if (value < 0) JMessageUtil.error(MainFrame.i18n.get("test.temp0129"));
                else fileVersion = value;
            } catch (NumberFormatException ex) {
                JMessageUtil.error(MainFrame.i18n.get("test.temp0129"));
            }
        }

        File folder = FileDialog.chooseOpenFolder(MainFrame.i18n.get("test.temp0130"));
        if (folder == null) {
            log.info(MainFrame.i18n.get("test.temp0131"));
            return;
        }

        // 收集所有选中的文件夹
        List<WzFolder> wzFolders = new ArrayList<>();
        for (TreePath treePath : selectedPaths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
            WzObject wzObject = (WzObject) node.getUserObject();
            if (wzObject instanceof WzFolder f) {
                wzFolders.add(f);
            }
        }
        if (wzFolders.isEmpty()) return;

        Short finalFileVersion = fileVersion;
        String saveBasePath = folder.getAbsolutePath();
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                for (WzFolder wzFolder : wzFolders) {
                    if (isCancelled()) return null;

                    if (wzFolder.getName().equals("Data")) {
                        List<WzObject> children = wzFolder.getChildren();
                        int total = children.size() + 1;
                        int count = 0;
                        SwingUtilities.invokeLater(() -> MainFrame.getInstance().updateProgress(0, total));

                        String savePath = Path.of(saveBasePath, "Base.wz").toString();
                        packageBase(finalFileVersion, wzFolder, savePath);
                        count++;
                        int finalCount = count;
                        SwingUtilities.invokeLater(() -> MainFrame.getInstance().updateProgress(finalCount, total));

                        for (WzObject wzObject : children) {
                            if (isCancelled()) return null;
                            if (wzObject instanceof WzFolder child) {
                                savePath = Path.of(saveBasePath, child.getName() + ".wz").toString();
                                packageFolder(finalFileVersion, child, savePath);
                            }
                            count++;
                            int finalCount2 = count;
                            SwingUtilities.invokeLater(() -> MainFrame.getInstance().updateProgress(finalCount2, total));
                        }
                    } else {
                        String savePath = Path.of(saveBasePath, wzFolder.getName()).toString();
                        if (!savePath.endsWith(".wz")) savePath = savePath + ".wz";
                        packageFolder(finalFileVersion, wzFolder, savePath);
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    get();
                    String name = wzFolders.size() == 1 ? wzFolders.get(0).getName()
                            : wzFolders.size() + "个文件夹";
                    MainFrame.getInstance().setStatusText(MainFrame.i18n.get("status.package_success", name));
                } catch (Exception ex) {
                    log.error("打包异常", ex);
                }
            }
        };
        editPane.trackWorker(worker);
        worker.execute();
    }

    private void packageBase(short fileVersion, WzFolder wzFolder, String savePath) {
        Set<String> directories = new HashSet<>();
        List<WzImageFile> imageFiles = new ArrayList<>();
        for (WzObject child : wzFolder.getChildren()) {
            if (child instanceof WzFolder directory) {
                directories.add(directory.getName());
            } else if (child instanceof WzImageFile imageFile) {
                imageFiles.add(imageFile);
            }
        }

        WzFile wzFile = WzFile.createNewFile(savePath, fileVersion, wzFolder.getKeyBoxName(), wzFolder.getIv(), wzFolder.getKey());
        directories.forEach(directory -> wzFile.getWzDirectory().addChild(new WzDirectory(directory, wzFile.getWzDirectory(), wzFile)));
        imageFiles.forEach(imageFile -> {
            if (!imageFile.parse(false)) {
                MainFrame.getInstance().setStatusTextWithErrLog(MainFrame.i18n.get("error.parse", imageFile.getName(), imageFile.getStatus().getMessage()));
                throw new RuntimeException();
            }
            wzFile.getWzDirectory().addChild(imageFile);
        });
        wzFile.save();
    }

    private void packageFolder(short fileVersion, WzFolder wzFolder, String savePath) {
        WzFile wzFile = WzFile.createNewFile(savePath, fileVersion, wzFolder.getKeyBoxName(), wzFolder.getIv(), wzFolder.getKey());
        packageSubToWz(wzFolder, wzFile.getWzDirectory());
        MainFrame.getInstance().setStatusText(MainFrame.i18n.get("status.package_start", wzFile.getName()));
        wzFile.save();
        MainFrame.getInstance().setStatusText(MainFrame.i18n.get("status.package_success", wzFile.getName()));
    }

    private void packageSubToWz(WzFolder wzFolder, WzDirectory parent) {
        List<WzObject> children = wzFolder.getChildren();
        int total = children.size();
        int current = 0;
        MainFrame.getInstance().setStatusText(MainFrame.i18n.get("status.package_running", wzFolder.getName()));
        for (WzObject child : children) {
            if (child instanceof WzFolder subFolder) {
                WzDirectory wzDirectory = new WzDirectory(child.getName(), parent, parent.getWzFile());
                packageSubToWz(subFolder, wzDirectory);
                parent.addChild(wzDirectory);
            } else if (child instanceof WzImageFile imageFile) {
                if (!imageFile.parse(false)) {
                    MainFrame.getInstance().setStatusTextWithErrLog(MainFrame.i18n.get("error.parse", imageFile.getName(), imageFile.getStatus().getMessage()));
                    throw new RuntimeException();
                }
                parent.addChild(imageFile);
            } else if (child instanceof WzXmlFile xmlFile) {
                if (!xmlFile.parse()) {
                    MainFrame.getInstance().setStatusTextWithErrLog(MainFrame.i18n.get("error.parse", xmlFile.getName(), xmlFile.getStatus().getMessage()));
                    throw new RuntimeException();
                }
                parent.addChild(xmlFile);
            }
            MainFrame.getInstance().updateProgress(++current, total);
        }
    }
}
