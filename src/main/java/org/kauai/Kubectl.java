package org.kauai;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Kubectl {
    Map<String, KObjectMetadata> kobjects;

    public Kubectl() {
        kobjects = new HashMap<>();

        // Pods
        KObjectMetadata metadata = new KObjectMetadata("pod", "metadata.name", "metadata.namespace", "status.containerStatuses[].state.[keys]");
        metadata.addActions(KAction.Type.SHELL, KAction.Type.LOGS, KAction.Type.DESCRIBE);
        kobjects.put("pod", metadata);

        // Services
        metadata = new KObjectMetadata("service", "metadata.name", "metadata.namespace", "spec.type", "spec.clusterIP", "spec.ports[].port");
        metadata.addActions(KAction.Type.DESCRIBE);
        kobjects.put("service", metadata);

        // Deployments
        metadata = new KObjectMetadata("deployment", "metadata.name", "metadata.namespace", "status.replicas", "status.conditions[].type");
        metadata.addActions(KAction.Type.DESCRIBE);
        kobjects.put("deployment", metadata);
    }

    public KObjectMetadata getMetadata(String kobject) {
        return kobjects.get(kobject);
    }

    public List<KObject> get(String kobject) throws Exception {
        KObjectMetadata metadata = kobjects.get(kobject);

        JSONObject kres = call("get", kobject);
        JSONArray items = (JSONArray) kres.get("items");
        return items.stream().map(o -> getKObject(metadata, o)).toList();
    }

    private KObject getKObject(KObjectMetadata metadata, Object o) {
        String[] values = new String[metadata.getFields().length];

        int index = 0;
        for (String field : metadata.getFields()) {
            List<String> fieldPath = Arrays.stream(field.split("\\.")).toList();
            values[index] = getValue((JSONObject) o, fieldPath);
            index++;
        }
        return new KObject(metadata, values);
    }

    private String getValue(JSONObject obj, List<String> path) {
        JSONArray arr = null;

        String pathStep = path.get(0);
        if (pathStep.endsWith("[]")) {
            arr = (JSONArray) obj.get(pathStep.substring(0, pathStep.length() - 2));
        }

        if (path.size() == 1) {
            if (arr != null) {
                return "";
            } else if (pathStep.equals("[keys]")) {
                return String.join(", ", obj.keySet());
            } else {
                return obj.get(pathStep).toString();
            }
        }

        List<String> nextPath = path.subList(1, path.size());
        if (arr != null) {
            return String.join(", ", arr.stream().map(a -> getValue((JSONObject) a, nextPath)).sorted().toList());
        } else {
            return getValue((JSONObject) obj.get(pathStep), nextPath);
        }
    }

    public JSONObject call(String ...command) throws Exception {
        Runtime rt = Runtime.getRuntime();
        List<String> commands = new ArrayList<>();
        commands.add("kubectl");
        commands.addAll(Arrays.stream(command).toList());
        commands.add("--all-namespaces");
        commands.add("-o");
        commands.add("json");
        System.out.println(String.join(" ", commands));
        ProcessBuilder pb = new ProcessBuilder(commands);
        Process proc = pb.start();
        BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

        StringBuilder sb = new StringBuilder();
        String s = null;
        while ((s = stdInput.readLine()) != null) {
            sb.append(s);
        }

        String output = sb.toString();
        Object obj = new JSONParser().parse(output);
        return (JSONObject) obj;
    }
}
