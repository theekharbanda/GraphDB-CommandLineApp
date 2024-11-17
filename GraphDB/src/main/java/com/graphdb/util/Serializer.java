package com.graphdb.util;

import com.graphdb.modal.Node;

import java.io.*;
import java.nio.file.*;
import java.util.logging.*;

public class Serializer {
    private static final Logger LOGGER = Logger.getLogger(Serializer.class.getName());

    public void serialize(Node document, String filePath) {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(Paths.get(filePath))))) {
            oos.writeObject(document);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to serialize document", e);
            throw new RuntimeException("Serialization failed", e);
        }
    }

    public Object deserialize(String filePath) {
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(Paths.get(filePath))))) {
            return (Object) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Failed to deserialize document", e);
            throw new RuntimeException("Deserialization failed", e);
        }
    }
}