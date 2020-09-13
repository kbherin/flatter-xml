package com.karbherin.flatterxml.helper;

import com.karbherin.flatterxml.model.Pair;

public class ParsingHelpers {

    /**
     * Denotes that a tag was not found
     */
    public static final Pair<Integer, Integer> TAG_NOTFOUND_COORDS = new Pair<>(0, -1);

    /**
     * Locates the root tag.
     * @param str   - character sequence to search root tag in
     * @param limit - end of search window
     */
    public static Pair<Integer, Integer> locateRootTag(char[] str, int limit) {
        Pair<Integer, Integer> coords = nextTagCoords(str, 0, limit);
        while (coords != TAG_NOTFOUND_COORDS
                && !isStartTag(str, coords.getKey(), 1+coords.getVal())) {

            coords = nextTagCoords(str, 1 + coords.getVal(), limit);
        }
        return coords;
    }

    /**
     * Finds the location of the next tag whether that is a start tag or an end tag.
     * @param str    - the character array to find the end tag in
     * @param start  - stop searching at this position
     * @param limit  - search starting from limit-1 position
     */
    public static Pair<Integer, Integer> nextTagCoords(final char[] str, final int start, final int limit) {
        int startDelimPos = indexOf('<', str, start, limit);
        if (startDelimPos < 0) {
            return TAG_NOTFOUND_COORDS;
        }
        int endDelimPos   = indexOf('>', str, startDelimPos, limit);
        if (endDelimPos < 0) {
            return TAG_NOTFOUND_COORDS;
        }
        return new Pair<Integer, Integer>(startDelimPos, endDelimPos);
    }

    /**
     * Finds the location of a tag from the end whether that is a start tag or an end tag.
     * @param str    - the character array to find the end tag in
     * @param start  - stop searching at this position
     * @param limit  - search starting from limit-1 position
     */
    public static Pair<Integer, Integer> lastTagCoords(final char[] str, final int start, final int limit) {
        int endDelimPos = lastIndexOf('>', str, start, limit);
        if (endDelimPos < 0) {
            return TAG_NOTFOUND_COORDS;
        }
        int startDelimPos = lastIndexOf('<', str, start, endDelimPos);
        if (startDelimPos < 0) {
            return TAG_NOTFOUND_COORDS;
        }
        return new Pair<Integer, Integer>(startDelimPos, endDelimPos);
    }

    /**
     * Finds the position of the last occurrence of search string in the target string.
     * @param search - string to search for
     * @param target    - target string to search in
     * @param start  - stop searching at this position
     * @param limit  - start searching from limit-1 position
     */
    public static Pair<Integer, Integer> lastIndexOf(final char[] search,
                                                     final char[] target, final int start, final int limit) {
        int lastIdx = search.length-1;
        int j = lastIdx;

        for (int i = lastIndexOf(search[search.length-1], target, start, limit); i >= start; i--) {
            char t = search[j], s = target[i];
            if (t == s) {
                j--;               // Continue matching
                if (j < 0) {       // Full match
                    return new Pair<>(i, i+search.length-1);
                }
            } else {
                // Matching stopped
                j = lastIdx; // reset to last index of limit tag
                i = 1+lastIndexOf(search[search.length-1], target, start, i);
            }
        }

        return TAG_NOTFOUND_COORDS;
    }

    /**
     * Finds the position of the first occurrence of search string in the target string.
     * @param search - string to search for
     * @param target    - target string to search in
     * @param start  - start searching at this position
     * @param limit  - stop searching from limit-1 position
     */
    public static Pair<Integer, Integer> indexOf(final char[] search,
                                                 final char[] target, final int start, final int limit) {
        int j = 0;

        for (int i = indexOf(search[0], target, start, limit); i >= 0 && i < limit; i++) {
            char t = search[j], s = target[i];
            if (t == s) {
                j++;                              // Continue matching
                if (j == search.length) {      // Full match
                    return new Pair<>(i - search.length + 1, i);
                }
            } else {
                // Matching stopped
                j = 0; // reset to last index of limit tag
                i = indexOf(search[0], target, i, limit) - 1;
            }
        }
        return TAG_NOTFOUND_COORDS;
    }

    /**
     * Determines if the string between start and limit-1 is an end XML tag.
     * Assumes the string piece is a properly formed tag.
     * @param str   - string to test
     * @param start - starting location to test for ending tag form
     * @param limit   - ending location to test for ending tag form
     */
    public static boolean isEndTag(char[] str, int start, int limit) {
        return str[start+1] == '/' && str[start] == '<' && str[limit-1] == '>';
    }

    /**
     * Determines if the string between start and limit-1 is a start XML tag.
     * Assumes the string piece is a properly formed tag.
     * @param str   - string to test
     * @param start - starting location to test for ending tag form
     * @param limit   - ending location to test for ending tag form
     */
    public static boolean isStartTag(char[] str, int start, int limit) {
        return str[start] == '<' && str[limit-1] == '>' && isXmlElementIdentifierStart(str[start+1]);
    }

    /**
     * Tests if the second string within start and limit-1 locations matches with contents of the first string.
     * @param searchStr - expected character sequence
     * @param str       - string to test
     * @param start     - starting location to test for ending tag form
     * @param limit       - ending location to test for ending tag form
     */
    public static boolean isNamedTag(char[] searchStr, char[] str, int start, int limit) {
        int i;
        for (i = start; i < limit && searchStr[i] == str[i]; i++);
        return i < limit;
    }

    /**
     * Determines if given character is a letter or underscore.
     * An XML element cannot start with a digit.
     * @param ch - character to test
     * @return
     */
    public static boolean isXmlElementIdentifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    /**
     * Find location of a character in a string between start and limit-1 positions in the string.
     * @param target
     * @param search
     * @param start
     * @param limit
     * @return
     */
    public static int indexOf(final char search, final char[] target, final int start, final int limit) {
        int pos = start;
        for (; pos < limit && search != target[pos]; pos++);
        return pos == target.length ? -1 : pos;
    }

    /**
     * Find location of a character in a string starting from limit-1 position up to start position.
     * @param target
     * @param search
     * @param start
     * @param limit
     * @return
     */
    public static int lastIndexOf(final char search, final char[] target, final int start, final int limit) {
        int pos = limit - 1;
        for (; pos >= start && search != target[pos]; pos--);
        return pos;
    }

}
