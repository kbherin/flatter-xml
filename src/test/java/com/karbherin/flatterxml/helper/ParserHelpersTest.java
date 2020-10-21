package com.karbherin.flatterxml.helper;

import static org.junit.Assert.*;

import com.karbherin.flatterxml.model.Pair;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.karbherin.flatterxml.helper.ParsingHelpers.*;
public class ParserHelpersTest {

    private static String targetStr = "                  :phones>"
            + "                      <ph:phone>"
            + "                        <ph:phone-num>1234567890</ph:phone-num>"
            + "                        <ph:phone-type>landline</ph:phone-type>"
            + "                      </ph:phone>"
            + "                    </ph:phones>"
            + "                  </emp:contact>"
            + "                </emp:employee>"
            + "                <emp:employee>"
            + "                  <emp:employee-no>00000002</emp:employee-no>"
            + "                  <emp:employee-name>Tony Stark</emp:employee-name>"
            + "                  <emp:depar";

    private static String junkStr = "<wlfjwo></wlfjwo>";



    @Test
    public void testLastIndexOf() {
        /*assertNotEquals(TAG_NOTFOUND_COORDS, lastIndexOf("</emp:employee>".toCharArray(), targetStr.toCharArray(), 0, targetStr.length()));
        assertEquals(TAG_NOTFOUND_COORDS, lastIndexOf("</emp:employee>".toCharArray(), junkStr.toCharArray(), 0, junkStr.length()));*/
        Pair<Integer, Integer> coord =  lastIndexOf("abc", "123abc456abc789", 1);
        assertEquals("\"3\" : \"5\"", coord.toString());
    }

    @Test
    public void testIndexOf() {
        assertNotEquals(TAG_NOTFOUND_COORDS, indexOf("</emp:employee>", targetStr, 0));
        assertEquals(TAG_NOTFOUND_COORDS, indexOf("</emp:employee>", junkStr, 0));

        Pair<Integer, Integer> coord =  indexOf("abc", "123abc456abc789", 1);
        assertEquals("\"3\" : \"5\"", coord.toString());
    }

    @Test
    public void testElemAttr() {
        Pattern elemAttrRx = Pattern.compile("^(?<elem>.*?)(\\[(?<attr>.*?)?\\])?$");
        Matcher mat = elemAttrRx.matcher("ph:phone-num");
        assertTrue(mat.find());
        if (mat.find()) {
            assertEquals("ph:phone-num", mat.group("elem"));
            assertNull(mat.group("attr"));
        }

        mat = elemAttrRx.matcher("ph:phone-num[ph:contact-type]");
        assertTrue(mat.find());
        if (mat.find()) {
            assertEquals("ph:phone-num", mat.group("elem"));
            assertEquals("ph:contact-type", mat.group("attr"));
        }
    }
}
