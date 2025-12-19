/*
   Copyright (C) 2015 Myra Fuchs, Linda Spindler, Clemens Hlawacek, Ebenezer Bonney Ussher,
   Martin Bayerl, Christoph Krasa

   This file is part of PicFrame.

   PicFrame is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PicFrame is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with PicFrame.  If not, see <http://www.gnu.org/licenses/>.
*/

package picframe.at.picframe.helper.viewpager;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the history of viewed pages in the slideshow.
 * This allows users to navigate back to previously viewed photos,
 * which is especially useful when viewing photos in random order.
 */
public class SlideshowHistory {
    
    private final List<Integer> history;
    private int currentPosition;
    private static final int MAX_HISTORY_SIZE = 1000;
    
    public SlideshowHistory() {
        history = new ArrayList<>();
        currentPosition = -1;
    }
    
    /**
     * Records a page visit. If we're in the middle of history (user went back),
     * this truncates the forward history and adds the new page.
     * 
     * @param pageIndex The index of the page that was visited
     */
    public void recordPageVisit(int pageIndex) {
        // If we're not at the end of history, truncate forward history
        if (currentPosition < history.size() - 1) {
            // Remove all entries after current position
            while (history.size() > currentPosition + 1) {
                history.remove(history.size() - 1);
            }
        }
        
        // Don't add duplicate consecutive entries
        if (history.isEmpty() || history.get(history.size() - 1) != pageIndex) {
            history.add(pageIndex);
            
            // Limit history size to prevent memory issues
            if (history.size() > MAX_HISTORY_SIZE) {
                history.remove(0);
            }
        }
        
        currentPosition = history.size() - 1;
    }
    
    /**
     * Checks if there is a previous page in history.
     * 
     * @return true if we can go back
     */
    public boolean canGoBack() {
        return currentPosition > 0;
    }
    
    /**
     * Checks if there is a next page in history (if user went back and can go forward).
     * 
     * @return true if we can go forward
     */
    public boolean canGoForward() {
        return currentPosition < history.size() - 1;
    }
    
    /**
     * Gets the previous page index from history.
     * 
     * @return The previous page index, or -1 if there is no previous page
     */
    public int goBack() {
        if (canGoBack()) {
            currentPosition--;
            return history.get(currentPosition);
        }
        return -1;
    }
    
    /**
     * Gets the next page index from history.
     * 
     * @return The next page index, or -1 if there is no next page
     */
    public int goForward() {
        if (canGoForward()) {
            currentPosition++;
            return history.get(currentPosition);
        }
        return -1;
    }
    
    /**
     * Gets the current page index from history.
     * 
     * @return The current page index, or -1 if history is empty
     */
    public int getCurrentPage() {
        if (currentPosition >= 0 && currentPosition < history.size()) {
            return history.get(currentPosition);
        }
        return -1;
    }
    
    /**
     * Gets the current position in the history stack.
     * 
     * @return The current position (0-based index into history)
     */
    public int getHistoryPosition() {
        return currentPosition;
    }
    
    /**
     * Gets the total number of pages in history.
     * 
     * @return The history size
     */
    public int getHistorySize() {
        return history.size();
    }
    
    /**
     * Clears all history.
     */
    public void clear() {
        history.clear();
        currentPosition = -1;
    }
    
    /**
     * Checks if history is empty.
     * 
     * @return true if history is empty
     */
    public boolean isEmpty() {
        return history.isEmpty();
    }
}
