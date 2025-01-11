package com.example.smartbin.viewmodel;

public class LoginState {
    // State constants
    public static final int IDLE = 0;
    public static final int LOADING = 1;
    public static final int SUCCESS = 2;
    public static final int ERROR = 3;

    // Predefined static instances for common states
    public static final LoginState Idle = new LoginState(IDLE);
    public static final LoginState Loading = new LoginState(LOADING);
    public static final LoginState Success = new LoginState(SUCCESS);
    public static final LoginState Error = new LoginState(ERROR, "An error occurred");

    // Instance variables
    private final int state;
    private final String message;

    // Constructors
    public LoginState(int state) {
        this(state, null);
    }

    public LoginState(int state, String message) {
        this.state = state;
        this.message = message;
    }

    // Getters
    public int getState() {
        return state;
    }

    public String getMessage() {
        return message;
    }

    // Factory methods for custom error or success messages
    public static LoginState customError(String errorMessage) {
        return new LoginState(ERROR, errorMessage);
    }

    public static LoginState customSuccess(String successMessage) {
        return new LoginState(SUCCESS, successMessage);
    }
}
