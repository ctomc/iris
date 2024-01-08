package org.iris_events.events;

import java.util.List;
import java.util.UUID;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.constraints.NotNull;

import org.iris_events.annotations.Message;
import org.iris_events.annotations.MessageHandler;
import org.iris_events.annotations.Scope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import io.quarkus.security.Authenticated;

@ApplicationScoped
public class TestConsumers {
    @MessageHandler
    @Authenticated
    public PublicProfile identityAuthenticated(IdentityAuthenticated identityAuthenticated) {
        return new PublicProfile(UUID.randomUUID(), "test");
    }

    @MessageHandler
    @Authenticated
    public void addContact(AddContact addContact) {

    }

    @MessageHandler
    @Authenticated
    public void removeContact(RemoveContact removeContact) {

    }

    @MessageHandler
    public void verifyProfilePhoto(VerifyProfilePhoto verifyProfilePhoto) {

    }

    @MessageHandler(rolesAllowed = @RolesAllowed({ "**" }))
    public MediaUpload uploadImage(ImageUploadAnnouncement imageUploadAnnouncement) {
        return new MediaUpload(List.of(new Media("test", "test.txt", null, null, null)), UUID.randomUUID(), null);
    }

    @MessageHandler
    public ProfileImageRemoved removeProfileImage(ProfileImageRemove profileImageRemove) {
        return new ProfileImageRemoved();
    }

    enum NameType {
        CONTACT("contact"),
        IDENTITY("identity"),
        UNS("uns");

        private final String name;

        NameType(String uns) {
            this.name = uns;
        }

        public String getName() {
            return name;
        }

        @Override
        @JsonValue
        public String toString() {
            return name;
        }
    }

    @Message(name = "identity/authenticated")
    record IdentityAuthenticated(@JsonProperty("identity_id") UUID identityId) {
    }

    @Message(name = "identity/view-public-profile", scope = Scope.FRONTEND)
    record ViewPublicProfile(
            @JsonProperty("gid_uuid") @NotNull UUID gidUuid) {
    }

    @Message(name = "identity/add-contact", scope = Scope.FRONTEND)
    record AddContact(
            @JsonProperty("gid_uuid") @NotNull UUID gidUuid) {
    }

    @Message(name = "identity/remove-contact", scope = Scope.FRONTEND)
    record RemoveContact(
            @JsonProperty("gid_uuid") @NotNull UUID gidUuid) {
    }

    @Message(name = "identity/public-profile", scope = Scope.SESSION)
    record PublicProfile(
            @JsonProperty("gid_uuid") @NotNull UUID gidUuid,
            @JsonProperty("name") @NotNull String name) {
    }

    @Message(name = "directory/search-results", scope = Scope.SESSION)
    record SearchResults(
            Name[] names,
            int count) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Name(
            @JsonProperty("gid_uuid") UUID gidUuid,
            @JsonProperty("name") String name) {
    }

    @Message(name = "identity/image-upload", scope = Scope.FRONTEND)
    record ImageUploadAnnouncement(
            String name,
            @JsonProperty("content_type") String contentType) {
    }

    @Message(name = "identity/verify-profile-photo")
    record VerifyProfilePhoto(
            @JsonProperty("decryption_key") String decryptionKey,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("media_type") String mediaType,
            @JsonProperty("sha_512_sum") String sha512Sum,
            @JsonProperty("url") String url,
            @JsonProperty("tracking_id") UUID trackingId) {
    }

    @Message(name = "media/upload-internal")
    record MediaUpload(
            @JsonProperty("media") List<Media> media,
            @JsonProperty("gid_uuid") UUID gidUuid,
            @JsonProperty("metadata") String metadata) {
    }

    public record Media(
            String type,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("content_type") String contentType,
            String context,
            @JsonProperty("context_id") UUID contextId) {
    }

    @Message(name = "identity/image-uploaded")
    record UserImageUploaded(
            @JsonProperty("s3_bucket") String s3Bucket,
            @JsonProperty("s3_object_key") String s3ObjectKey,
            @JsonProperty("media_id") UUID mediaId,
            @JsonProperty("gid_uuid") UUID gidUuid,
            @JsonProperty("metadata") String metadata) {
    }

    @Message(name = "identity/profile-image-remove", scope = Scope.FRONTEND)
    record ProfileImageRemove() {
    }

    @Message(name = "identity/profile-image-removed", scope = Scope.SESSION)
    record ProfileImageRemoved() {
    }
}
