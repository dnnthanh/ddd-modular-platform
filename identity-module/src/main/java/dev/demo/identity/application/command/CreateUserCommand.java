package dev.demo.identity.application.command;

import java.util.List;

public record CreateUserCommand(String username, String password, List<String> roles) {}
