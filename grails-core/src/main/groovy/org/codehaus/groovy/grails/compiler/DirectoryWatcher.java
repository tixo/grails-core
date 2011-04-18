/*
 * Copyright 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.groovy.grails.compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to watch directories for changes
 *
 * @author Graeme Rocher
 * @since 1.4
 */
public class DirectoryWatcher extends Thread {

    private File[] directories;
    private String[] extensions;
    private List<FileChangeListener> listeners = new ArrayList<FileChangeListener>();

    private Map<File, Long> lastModifiedMap = new ConcurrentHashMap<File, Long>();
    private Map<File, Long> directoryWatch = new ConcurrentHashMap<File, Long>();
    private boolean active = true;
    private long sleepTime = 3000;

    public DirectoryWatcher(File[] directories, String[] extensions) {
        this.directories = directories;
        this.extensions = extensions;
        setDaemon(true);
    }

    /**
     * Sets whether to stop the directory watcher
     *
     * @param active False if you want to stop watching
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Sets the amount of time to sleep between checks
     *
     * @param sleepTime The sleep time
     */
    public void setSleepTime(long sleepTime) {
        this.sleepTime = sleepTime;
    }

    /**
     * Adds a file listener that can react to change events
     *
     * @param listener The file listener
     */
    public void addListener(FileChangeListener listener ) {
        this.listeners.add(listener);
    }

    /**
     * Adds a file to the watch list
     *
     * @param fileToWatch The file to watch
     */
    public void addWatchFile(File fileToWatch) {
        lastModifiedMap.put(fileToWatch, fileToWatch.lastModified());
    }

    /**
     * Adds a directory to watch for the given file and extensions
     *
     * @param dir The directory
     * @param extensions The extensions
     */
    public void addWatchDirectory(File dir, List<String> extensions) {
        cacheFilesForDirectory(dir, extensions.toArray(new String[extensions.size()]), false);
    }
    /**
     * Interface for FileChangeListeners
     */
    public static interface FileChangeListener {
        /**
         * Fired when a file changes
         *
         * @param file The file that changed
         */
        public abstract void onChange(File file);

        /**
         * Fired when a new file is created
         *
         * @param file The file that was created
         */
        public abstract void onNew(File file);
    }


    @Override
    public void run() {
        initializeLastModifiedCache(directories, extensions);
        int count = 0;
        while(active) {
            Set<File> files = lastModifiedMap.keySet();
            for (File file : files) {
                long currentLastModified = file.lastModified();
                Long cachedTime = lastModifiedMap.get(file);
                if(currentLastModified > cachedTime) {
                    lastModifiedMap.put(file, currentLastModified);
                    for (FileChangeListener listener : listeners) {
                        listener.onChange(file);
                    }
                }
            }
            try {
                count++;
                if(count > 2) {
                    count = 0;
                    checkForNewFiles();
                }
                sleep(sleepTime);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private void checkForNewFiles() {
        for (File directory : directoryWatch.keySet()) {
            final Long currentTimestamp = directoryWatch.get(directory);

            if(currentTimestamp < directory.lastModified()) {
                cacheFilesForDirectory(directory, extensions, true);
            }

        }
    }

    private void initializeLastModifiedCache(File[] directories, final String[] extensions) {

        for (File directory : directories) {
            cacheFilesForDirectory(directory, extensions, false);
        }

    }

    private void cacheFilesForDirectory(File directory, String[] extensions, boolean fireEvent) {
        directoryWatch.put(directory, directory.lastModified());
        File[] files = directory.listFiles();
        for (File file : files) {
            if(isValidFileToMonitor(file.getName(), extensions)) {
                if(!lastModifiedMap.containsKey(file) && fireEvent) {
                    for (FileChangeListener listener : listeners) {
                        listener.onNew(file);
                    }
                }
                lastModifiedMap.put(file, file.lastModified());
            }
            else if(file.isDirectory()) {
               cacheFilesForDirectory(file, extensions, fireEvent);
            }
        }
    }

    private boolean isValidFileToMonitor(String name, String[] extensions) {
        for (String extension : extensions) {
            if (name.endsWith(extension)) return true;
        }
        return false;
    }



}
