package com.chatsystem.backend.repository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
public class TestConnection {
    static public void main(String[] args) {
        try {
            // Load the PostgreSQL JDBC driver
            Class.forName("org.postgresql.Driver");

            // Establish a connection to the database
            String url = "jdbc:postgresql://localhost:5432/relay_chat";
            String username = "postgres"; // Replace with your actual username
            String password = "280206acr"; // Replace with your actual password
            Connection connection = DriverManager.getConnection(url, username, password);
            PreparedStatement preparedStatement = connection.prepareStatement("SELECT username, email, full_name, system_role FROM chat.users ");
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String usernameResult = resultSet.getString("username");
                String emailResult = resultSet.getString("email");
                String fullNameResult = resultSet.getString("full_name");
                String systemRoleResult = resultSet.getString("system_role");

                System.out.println("Username: " + usernameResult);
                System.out.println("Email: " + emailResult);
                System.out.println("Full Name: " + fullNameResult);
                System.out.println("System Role: " + systemRoleResult);
                System.out.println("---------------------------");
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
