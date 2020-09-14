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
     */
    public static Pair<Integer, Integer> locateRootTag(String str) {
        Pair<Integer, Integer> coords = nextTagCoord(str, 0);
        while (coords != TAG_NOTFOUND_COORDS
                && !isStartTag(str, coords.getKey(), 1+coords.getVal())) {

            coords = nextTagCoord(str, 1 + coords.getVal());
        }
        return coords;
    }

    /**
     * Finds the location of the next tag whether that is a start tag or an end tag.
     * @param str    - the character array to find the end tag in
     * @param start  - stop searching at this position
     */
    public static Pair<Integer, Integer> nextTagCoord(final String str, final int start) {
        int startDelimPos = str.indexOf('<', start);
        if (startDelimPos < 0) {
            return TAG_NOTFOUND_COORDS;
        }
        int endDelimPos   = str.indexOf('>', startDelimPos);
        if (endDelimPos < 0) {
            return TAG_NOTFOUND_COORDS;
        }
        return new Pair<Integer, Integer>(startDelimPos, endDelimPos);
    }

    /**
     * Finds the location of a tag from the end whether that is a start tag or an end tag.
     * @param str    - the character array to find the end tag in
     * @param start  - stop searching at this position
     */
    public static Pair<Integer, Integer> lastTagCoord(final String str, final int start) {
        int endDelimPos = str.lastIndexOf('>', start);
        if (endDelimPos < 0) {
            return TAG_NOTFOUND_COORDS;
        }
        int startDelimPos = str.lastIndexOf('<', start);
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
     */
    public static Pair<Integer, Integer> lastIndexOf(String search,
                                                     String target, final int start) {

        int begin = target.indexOf(search, start);
        if (begin < 0) {
            return TAG_NOTFOUND_COORDS;
        } else {
            return new Pair<>(begin, begin + search.length() - 1);
        }
    }

    /**
     * Finds the position of the first occurrence of search string in the target string.
     * @param search - string to search for
     * @param target    - target string to search in
     * @param start  - start searching at this position
     */
    public static Pair<Integer, Integer> indexOf(String search, String target, int start) {
        int begin = target.indexOf(search, start);
        if (begin < 0) {
            return TAG_NOTFOUND_COORDS;
        } else {
            return new Pair<>(begin, begin + search.length() - 1);
        }
    }

    /**
     * Determines if the string between start and limit-1 is an end XML tag.
     * Assumes the string piece is a properly formed tag.
     * @param str   - string to test
     * @param start - starting location to test for ending tag form
     * @param limit   - ending location to test for ending tag form
     */
    public static boolean isEndTag(String str, int start, int limit) {
        return str.charAt(start+1) == '/' && str.charAt(start) == '<' && str.charAt(limit-1) == '>'
                && isXmlElementIdentifierStart(str.charAt(start+2));
    }

    /**
     * Determines if the string between start and limit-1 is a start XML tag.
     * Assumes the string piece is a properly formed tag.
     * @param str   - string to test
     * @param start - starting location to test for ending tag form
     * @param limit   - ending location to test for ending tag form
     */
    public static boolean isStartTag(String str, int start, int limit) {
        return str.charAt(start) == '<' && str.charAt(limit-1) == '>'
                && isXmlElementIdentifierStart(str.charAt(start+1));
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

}