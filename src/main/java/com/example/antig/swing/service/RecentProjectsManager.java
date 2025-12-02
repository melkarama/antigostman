package com.example.antig.swing.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Manages the list of recently opened project files.
 */
public class RecentProjectsManager {
    private static final String PREFS_FILE = System.getProperty("user.home") + "/.postman-clone-recent.properties";
    private static final String RECENT_KEY_PREFIX = "recent.";
    private static final String THEME_KEY = "theme";
    private static final int MAX_RECENT = 10;
    
    private final List<String> recentProjects = new ArrayList<>();
    
    public RecentProjectsManager() {
        loadRecentProjects();
    }
    
    /**
     * Add a project file to the recent list.
     */
    public void addRecentProject(File file) {
        String path = file.getAbsolutePath();
        
        // Remove if already exists (to move to top)
        recentProjects.remove(path);
        
        // Add to beginning
        recentProjects.add(0, path);
        
        // Keep only MAX_RECENT items
        while (recentProjects.size() > MAX_RECENT) {
            recentProjects.remove(recentProjects.size() - 1);
        }
        
        saveRecentProjects();
    }
    
    /**
     * Get the list of recent project file paths.
     */
    public List<String> getRecentProjects() {
        // Filter out non-existent files
        List<String> existing = new ArrayList<>();
        for (String path : recentProjects) {
            if (new File(path).exists()) {
                existing.add(path);
            }
        }
        return existing;
    }
    
    /**
     * Clear all recent projects.
     */
    public void clearRecentProjects() {
        recentProjects.clear();
        saveRecentProjects();
    }
    
    private void loadRecentProjects() {
        Properties props = new Properties();
        File prefsFile = new File(PREFS_FILE);
        
        if (prefsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(prefsFile)) {
                props.load(fis);
                
                for (int i = 0; i < MAX_RECENT; i++) {
                    String path = props.getProperty(RECENT_KEY_PREFIX + i);
                    if (path != null && !path.isEmpty()) {
                        recentProjects.add(path);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void saveRecentProjects() {
        Properties props = new Properties();
        
        for (int i = 0; i < recentProjects.size(); i++) {
            props.setProperty(RECENT_KEY_PREFIX + i, recentProjects.get(i));
        }
        
        try (FileOutputStream fos = new FileOutputStream(PREFS_FILE)) {
            props.store(fos, "Recent Postman Clone Projects");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Get the theme preference ("light" or "dark").
     */
    public String getThemePreference() {
        Properties props = new Properties();
        File prefsFile = new File(PREFS_FILE);
        
        if (prefsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(prefsFile)) {
                props.load(fis);
                return props.getProperty(THEME_KEY, "dark"); // Default to dark
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "dark"; // Default to dark
    }
    
    /**
     * Set the theme preference ("light" or "dark").
     */
    public void setThemePreference(String theme) {
        Properties props = new Properties();
        File prefsFile = new File(PREFS_FILE);
        
        // Load existing properties first
        if (prefsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(prefsFile)) {
                props.load(fis);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        // Set theme
        props.setProperty(THEME_KEY, theme);
        
        // Save
        try (FileOutputStream fos = new FileOutputStream(PREFS_FILE)) {
            props.store(fos, "Postman Clone Preferences");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
