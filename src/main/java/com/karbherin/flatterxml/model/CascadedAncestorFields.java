package com.karbherin.flatterxml.model;

public interface CascadedAncestorFields extends RecordTypeHierarchy {

    Iterable<Pair<String, String>> getCascadedAncestorFields();

}
