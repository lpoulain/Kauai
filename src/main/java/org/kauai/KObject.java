package org.kauai;

public class KObject {
    private final KObjectMetadata metadata;
    private final String[] values;

    public KObjectMetadata getMetadata() {
        return metadata;
    }

    public String getName() {
        return values[0];
    }

    public String[] getValues() {
        return values;
    }

    public String geNamespace() {
        return values[1];
    }

    public boolean matches(String filter) {
        return values[0].toLowerCase().contains(filter.toLowerCase());
    }

    KObject(KObjectMetadata metadata, String... values) {
        this.metadata = metadata;
        this.values = values;
    }
}
