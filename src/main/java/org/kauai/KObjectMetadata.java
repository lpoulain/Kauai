package org.kauai;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class KObjectMetadata {
    private final String name;
    private final String[] fields;
    private final String[] labels;
    private final Set<KAction.Type> actions;

    public String getName() {
        return name;
    }

    public String[] getFields() {
        return fields;
    }

    public String[] getLabels() { return labels; }

    KObjectMetadata(String name, String... fields) {
        this.actions = new HashSet<>();
        this.name = name;
        this.fields = fields;
        this.labels = Arrays.stream(fields)
                .map(field -> {
                    String[] path = field.split("\\.");
                    for (int i=path.length-1; i >= 0; i--) {
                        String pathStep = path[i];
                        if (pathStep.equals("[keys]") || pathStep.equals("*")) {
                            continue;
                        }
                        if (pathStep.endsWith("[]")) {
                            return fancy(pathStep.substring(0, pathStep.length() - 2));
                        }
                        return fancy(pathStep);
                    }
                    return fancy(path[0]);
                })
                .toArray(String[]::new);
    }

    public void addActions(KAction.Type... actions) {
        this.actions.addAll(Arrays.stream(actions).toList());
    }

    public boolean supportsAction(KAction.Type action) {
        return this.actions.contains(action);
    }

    private String fancy(String label) {
        return label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1);
    }

}
