package com.karbherin.flatterxml.model;

import javax.xml.namespace.QName;

public interface RecordTypeHierarchy {

    QName recordName();

    RecordTypeHierarchy parentRecordType();

    int recordLevel();

}
