package sn.terrainapp.backend.auth;

public record TemporaryPasswordResponse(StaffUserResponse user, String temporaryPassword) {
}