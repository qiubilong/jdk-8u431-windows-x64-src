/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package javax.swing.plaf.basic;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractListModel;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.filechooser.FileSystemView;

import sun.awt.shell.ShellFolder;

/**
 * Basic implementation of a file list.
 *
 * @author Jeff Dinkins
 */
public class BasicDirectoryModel extends AbstractListModel<Object> implements PropertyChangeListener {

    private final JFileChooser filechooser;
    // PENDING(jeff) pick the size more sensibly
    private final Vector<File> fileCache = new Vector<File>(50);
    private LoadFilesThread loadThread = null;
    private Vector<File> files = null;
    private Vector<File> directories = null;
    private final AtomicInteger fetchID = new AtomicInteger();

    private PropertyChangeSupport changeSupport;

    private boolean busy = false;

    public BasicDirectoryModel(JFileChooser filechooser) {
        this.filechooser = filechooser;
        validateFileCache();
    }

    public void propertyChange(PropertyChangeEvent e) {
        String prop = e.getPropertyName();
        if(prop == JFileChooser.DIRECTORY_CHANGED_PROPERTY ||
           prop == JFileChooser.FILE_VIEW_CHANGED_PROPERTY ||
           prop == JFileChooser.FILE_FILTER_CHANGED_PROPERTY ||
           prop == JFileChooser.FILE_HIDING_CHANGED_PROPERTY ||
           prop == JFileChooser.FILE_SELECTION_MODE_CHANGED_PROPERTY) {
            validateFileCache();
        } else if ("UI".equals(prop)) {
            Object old = e.getOldValue();
            if (old instanceof BasicFileChooserUI) {
                BasicFileChooserUI ui = (BasicFileChooserUI) old;
                BasicDirectoryModel model = ui.getModel();
                if (model != null) {
                    model.invalidateFileCache();
                }
            }
        } else if ("JFileChooserDialogIsClosingProperty".equals(prop)) {
            invalidateFileCache();
        }
    }

    /**
     * This method is used to interrupt file loading thread.
     */
    public void invalidateFileCache() {
        synchronized (this) {
            if (loadThread != null) {
                loadThread.interrupt();
                loadThread = null;
                // Increment fetch ID to invalidate pending DoChangeContents
                fetchID.incrementAndGet();
            }
        }
    }

    public Vector<File> getDirectories() {
        synchronized(fileCache) {
            if (directories != null) {
                return directories;
            }
            Vector fls = getFiles();
            return directories;
        }
    }

    public Vector<File> getFiles() {
        synchronized(fileCache) {
            if (files != null) {
                return files;
            }
            files = new Vector<File>();
            directories = new Vector<File>();
            directories.addElement(filechooser.getFileSystemView().createFileObject(
                filechooser.getCurrentDirectory(), "..")
            );

            for (int i = 0; i < getSize(); i++) {
                File f = fileCache.get(i);
                if (filechooser.isTraversable(f)) {
                    directories.add(f);
                } else {
                    files.add(f);
                }
            }
            return files;
        }
    }

    public void validateFileCache() {
        File currentDirectory = filechooser.getCurrentDirectory();
        if (currentDirectory == null) {
            return;
        }

        synchronized (this) {
            if (loadThread != null) {
                loadThread.interrupt();
            }

            int fid = fetchID.incrementAndGet();
            setBusy(true, fid);
            loadThread = new LoadFilesThread(currentDirectory, fid);
            loadThread.start();
        }
    }

    /**
     * Renames a file in the underlying file system.
     *
     * @param oldFile a <code>File</code> object representing
     *        the existing file
     * @param newFile a <code>File</code> object representing
     *        the desired new file name
     * @return <code>true</code> if rename succeeded,
     *        otherwise <code>false</code>
     * @since 1.4
     */
    public boolean renameFile(File oldFile, File newFile) {
        synchronized(fileCache) {
            if (oldFile.renameTo(newFile)) {
                validateFileCache();
                return true;
            }
            return false;
        }
    }


    public void fireContentsChanged() {
        // System.out.println("BasicDirectoryModel: firecontentschanged");
        fireContentsChanged(this, 0, getSize()-1);
    }

    public int getSize() {
        return fileCache.size();
    }

    public boolean contains(Object o) {
        return fileCache.contains(o);
    }

    public int indexOf(Object o) {
        return fileCache.indexOf(o);
    }

    public Object getElementAt(int index) {
        return fileCache.get(index);
    }

    /**
     * Obsolete - not used.
     */
    public void intervalAdded(ListDataEvent e) {
    }

    /**
     * Obsolete - not used.
     */
    public void intervalRemoved(ListDataEvent e) {
    }

    protected void sort(Vector<? extends File> v){
        ShellFolder.sort(v);
    }

    // Obsolete - not used
    protected boolean lt(File a, File b) {
        // First ignore case when comparing
        int diff = a.getName().toLowerCase().compareTo(b.getName().toLowerCase());
        if (diff != 0) {
            return diff < 0;
        } else {
            // May differ in case (e.g. "mail" vs. "Mail")
            return a.getName().compareTo(b.getName()) < 0;
        }
    }


    private final class LoadFilesThread extends Thread {
        private final FileSystemView fileSystemView;
        private final boolean useFileHiding;
        private final boolean fileSelectionEnabled;
        private final int fid;
        private final File currentDirectory;
        private volatile DoChangeContents runnable;

        private LoadFilesThread(File currentDirectory, int fid) {
            super("Basic L&F File Loading Thread");
            this.currentDirectory = currentDirectory;
            this.fid = fid;
            fileSystemView = filechooser.getFileSystemView();
            useFileHiding = filechooser.isFileHidingEnabled();
            fileSelectionEnabled = filechooser.isFileSelectionEnabled();
        }

        public void run() {
            run0();
            setBusy(false, fid);
        }

        private void run0() {
            if (isInterrupted()) {
                return;
            }

            File[] list = fileSystemView.getFiles(currentDirectory, useFileHiding);

            if (isInterrupted()) {
                return;
            }

            final Vector<File> newFileCache = new Vector<File>();
            final Vector<File> newFiles = new Vector<File>();

            // Run through the file list, add directories and selectable files to fileCache
            // Note that this block must be OUTSIDE of Invoker thread because of
            // deadlock possibility with custom synchronized FileSystemView
            for (File file : list) {
                if (filechooser.accept(file)) {
                    boolean isTraversable = filechooser.isTraversable(file);

                    if (isTraversable) {
                        newFileCache.addElement(file);
                    } else if (fileSelectionEnabled) {
                        newFiles.addElement(file);
                    }

                    if (isInterrupted()) {
                        return;
                    }
                }
            }

            // First sort alphabetically by filename
            sort(newFileCache);
            sort(newFiles);

            newFileCache.addAll(newFiles);

            // To avoid loads of synchronizations with Invoker and improve performance we
            // execute the whole block on the COM thread
            DoChangeContents runnable = ShellFolder.invoke(new Callable<DoChangeContents>() {
                public DoChangeContents call() {
                    synchronized (fileCache) {
                        int newSize = newFileCache.size();
                        int oldSize = fileCache.size();

                        if (newSize > oldSize) {
                            //see if interval is added
                            int start = oldSize;
                            int end = newSize;
                            for (int i = 0; i < oldSize; i++) {
                                if (!newFileCache.get(i).equals(fileCache.get(i))) {
                                    start = i;
                                    for (int j = i; j < newSize; j++) {
                                        if (newFileCache.get(j).equals(fileCache.get(i))) {
                                            end = j;
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }

                            if (start >= 0 && end > start
                                && newFileCache.subList(end, newSize)
                                               .equals(fileCache.subList(start, oldSize))) {
                                if (isInterrupted()) {
                                    return null;
                                }
                                return new DoChangeContents(newFileCache.subList(start, end),
                                                            start, null, 0, fid);
                            }
                        } else if (newSize < oldSize) {
                            //see if interval is removed
                            int start = -1;
                            int end = -1;
                            for (int i = 0; i < newSize; i++) {
                                if (!newFileCache.get(i).equals(fileCache.get(i))) {
                                    start = i;
                                    end = i + oldSize - newSize;
                                    break;
                                }
                            }

                            if (start >= 0 && end > start
                                && fileCache.subList(end, oldSize)
                                            .equals(newFileCache.subList(start, newSize))) {
                                if (isInterrupted()) {
                                    return null;
                                }
                                return new DoChangeContents(null, 0,
                                                            new Vector<>(fileCache.subList(start, end)), start, fid);
                            }
                        }
                        if (!fileCache.equals(newFileCache)) {
                            if (isInterrupted()) {
                                return null;
                            }
                            return new DoChangeContents(newFileCache, 0, fileCache, 0, fid);
                        }
                        return null;
                    }
                }
            });

            if (runnable != null && !isInterrupted()) {
                SwingUtilities.invokeLater(runnable);
            }
        }
   }


    /**
     * Adds a PropertyChangeListener to the listener list. The listener is
     * registered for all bound properties of this class.
     * <p>
     * If <code>listener</code> is <code>null</code>,
     * no exception is thrown and no action is performed.
     *
     * @param    listener  the property change listener to be added
     *
     * @see #removePropertyChangeListener
     * @see #getPropertyChangeListeners
     *
     * @since 1.6
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        if (changeSupport == null) {
            changeSupport = new PropertyChangeSupport(this);
        }
        changeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener from the listener list.
     * <p>
     * If listener is null, no exception is thrown and no action is performed.
     *
     * @param listener the PropertyChangeListener to be removed
     *
     * @see #addPropertyChangeListener
     * @see #getPropertyChangeListeners
     *
     * @since 1.6
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        if (changeSupport != null) {
            changeSupport.removePropertyChangeListener(listener);
        }
    }

    /**
     * Returns an array of all the property change listeners
     * registered on this component.
     *
     * @return all of this component's <code>PropertyChangeListener</code>s
     *         or an empty array if no property change
     *         listeners are currently registered
     *
     * @see      #addPropertyChangeListener
     * @see      #removePropertyChangeListener
     * @see      java.beans.PropertyChangeSupport#getPropertyChangeListeners
     *
     * @since 1.6
     */
    public PropertyChangeListener[] getPropertyChangeListeners() {
        if (changeSupport == null) {
            return new PropertyChangeListener[0];
        }
        return changeSupport.getPropertyChangeListeners();
    }

    /**
     * Support for reporting bound property changes for boolean properties.
     * This method can be called when a bound property has changed and it will
     * send the appropriate PropertyChangeEvent to any registered
     * PropertyChangeListeners.
     *
     * @param propertyName the property whose value has changed
     * @param oldValue the property's previous value
     * @param newValue the property's new value
     *
     * @since 1.6
     */
    protected void firePropertyChange(String propertyName,
                                      Object oldValue, Object newValue) {
        if (changeSupport != null) {
            changeSupport.firePropertyChange(propertyName,
                                             oldValue, newValue);
        }
    }


    /**
     * Set the busy state for the model. The model is considered
     * busy when it is running a separate (interruptable)
     * thread in order to load the contents of a directory.
     */
    private synchronized void setBusy(final boolean busy, int fid) {
        if (fid == fetchID.get()) {
            boolean oldValue = this.busy;
            this.busy = busy;

            if (changeSupport != null && busy != oldValue) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        firePropertyChange("busy", !busy, busy);
                    }
                });
            }
        }
    }


    private final class DoChangeContents implements Runnable {
        private final List<File> addFiles;
        private final List<File> remFiles;
        private final int fid;
        private final int addStart;
        private final int remStart;

        private DoChangeContents(List<File> addFiles, int addStart,
                                 List<File> remFiles, int remStart,
                                 int fid) {
            this.addFiles = addFiles;
            this.addStart = addStart;
            this.remFiles = remFiles;
            this.remStart = remStart;
            this.fid = fid;
        }

        @Override
        public void run() {
            if (fetchID.get() != fid) {
                return;
            }

            final int remSize = (remFiles == null) ? 0 : remFiles.size();
            final int addSize = (addFiles == null) ? 0 : addFiles.size();
            final int cacheSize;
            synchronized (fileCache) {
                if (remSize > 0) {
                    fileCache.removeAll(remFiles);
                }
                if (addSize > 0) {
                    fileCache.addAll(addStart, addFiles);
                }
                files = null;
                directories = null;
                cacheSize = fileCache.size();
            }
            if (remSize > 0 && addSize == 0) {
                fireIntervalRemoved(BasicDirectoryModel.this, remStart, remStart + remSize - 1);
            } else if (addSize > 0 && remSize == 0 && addStart + addSize <= cacheSize) {
                fireIntervalAdded(BasicDirectoryModel.this, addStart, addStart + addSize - 1);
            } else {
                fireContentsChanged();
            }
        }
    }
}
