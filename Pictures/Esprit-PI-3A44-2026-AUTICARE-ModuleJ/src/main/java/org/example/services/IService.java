package org.example.services;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface IService<T> {
    void add(T t) throws SQLException;

    void update(T t) throws SQLException;

    void delete(int id) throws SQLException;

    Optional<T> findById(int id) throws SQLException;

    List<T> findAll() throws SQLException;
}
