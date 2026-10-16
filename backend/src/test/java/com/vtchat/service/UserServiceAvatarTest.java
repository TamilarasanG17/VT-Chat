package com.vtchat.service;

import com.vtchat.entity.User;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.UserRepository;
// import com.vtchat.security.TokenService;
import com.vtchat.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceAvatarTest {

    private static final String UUID1 = "3f2c1a9e-7b4d-4c1e-9a55-0123456789ab";
    private static final String UUID2 = "aaaaaaaa-7b4d-4c1e-9a55-0123456789ab";
    private static final String OLD = "avatars/7/" + UUID1 + ".jpg";
    private static final String NEW = "avatars/7/" + UUID2 + ".jpg";

    @Mock
    UserRepository users;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    AvatarStorageService avatarStorage;
    @Mock
    TokenService tokenService;
    @Mock
    UserCache userCache;
    @InjectMocks
    UserService service;

    private final MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", new byte[] { 1 });

    private User userWith(String picture) {
        return User.builder().id(7L).username("u").email("u@example.com").profilePicture(picture).build();
    }

    @Test
    void replacingStoresThenSwapsThenDeletesTheOldObject() {
        when(users.findById(7L)).thenReturn(Optional.of(userWith(OLD)));
        when(avatarStorage.store(file, 7L)).thenReturn(NEW);
        when(users.replaceProfilePicture(7L, OLD, NEW)).thenReturn(1);
        when(users.countByProfilePicture(OLD)).thenReturn(0L);
        service.uploadProfilePicture(7L, file);
        InOrder order = Mockito.inOrder(avatarStorage, users);
        order.verify(avatarStorage).store(file, 7L);
        order.verify(users).replaceProfilePicture(7L, OLD, NEW);
        order.verify(avatarStorage).delete(OLD);
        verify(avatarStorage, never()).delete(NEW);
        verify(userCache).invalidate(7L);
    }

    @Test
    void firstPictureHasNothingToDelete() {
        when(users.findById(7L)).thenReturn(Optional.of(userWith(null)));
        when(avatarStorage.store(file, 7L)).thenReturn(NEW);
        when(users.setProfilePictureIfNone(7L, NEW)).thenReturn(1);
        service.uploadProfilePicture(7L, file);
        verify(avatarStorage, never()).delete(anyString());
    }

    @Test
    void databaseFailureRemovesTheNewObjectAndKeepsTheOldOne() {
        when(users.findById(7L)).thenReturn(Optional.of(userWith(OLD)));
        when(avatarStorage.store(file, 7L)).thenReturn(NEW);
        when(users.replaceProfilePicture(7L, OLD, NEW)).thenThrow(new IllegalStateException("db"));
        assertThrows(IllegalStateException.class, () -> service.uploadProfilePicture(7L, file));
        verify(avatarStorage).delete(NEW);
        verify(avatarStorage, never()).delete(OLD);
    }

    @Test
    void failedUploadChangesNothingInTheDatabase() {
        when(users.findById(7L)).thenReturn(Optional.of(userWith(OLD)));
        when(avatarStorage.store(file, 7L))
                .thenThrow(new StorageException(StorageException.Kind.UNAVAILABLE, "upload"));
        assertThrows(StorageException.class, () -> service.uploadProfilePicture(7L, file));
        verify(users, never()).replaceProfilePicture(anyLong(), anyString(), anyString());
        verify(avatarStorage, never()).delete(anyString());
    }

    @Test
    void invalidImageIsRejectedBeforeAnyDatabaseChange() {
        when(users.findById(7L)).thenReturn(Optional.of(userWith(OLD)));
        when(avatarStorage.store(file, 7L)).thenThrow(ApiException.badRequest("INVALID_IMAGE", "bad"));
        assertThrows(ApiException.class, () -> service.uploadProfilePicture(7L, file));
        verify(users, never()).replaceProfilePicture(anyLong(), anyString(), anyString());
    }

    @Test
    void losingARaceDeletesOnlyTheLosersObject() {
        when(users.findById(7L)).thenReturn(Optional.of(userWith(OLD)));
        when(avatarStorage.store(file, 7L)).thenReturn(NEW);
        when(users.replaceProfilePicture(7L, OLD, NEW)).thenReturn(0);
        ApiException e = assertThrows(ApiException.class, () -> service.uploadProfilePicture(7L, file));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(avatarStorage).delete(NEW);
        verify(avatarStorage, never()).delete(OLD);
    }

    @Test
    void anObjectStillReferencedByAnotherRecordIsNotDeleted() {
        when(users.findById(7L)).thenReturn(Optional.of(userWith(OLD)));
        when(avatarStorage.store(file, 7L)).thenReturn(NEW);
        when(users.replaceProfilePicture(7L, OLD, NEW)).thenReturn(1);
        when(users.countByProfilePicture(OLD)).thenReturn(1L);
        service.uploadProfilePicture(7L, file);
        verify(avatarStorage, never()).delete(OLD);
    }

    @Test
    void legacyPictureIsReplacedAndItsLocalFileRemoved() {
        String legacy = UUID1 + ".jpg";
        when(users.findById(7L)).thenReturn(Optional.of(userWith(legacy)));
        when(avatarStorage.store(file, 7L)).thenReturn(NEW);
        when(users.replaceProfilePicture(7L, legacy, NEW)).thenReturn(1);
        when(users.countByProfilePicture(legacy)).thenReturn(0L);
        service.uploadProfilePicture(7L, file);
        verify(avatarStorage).delete(legacy);
    }

    @Test
    void removingClearsTheReferenceThenDeletes() {
        when(users.findById(7L)).thenReturn(Optional.of(userWith(OLD)), Optional.of(userWith(null)));
        when(users.clearProfilePicture(7L, OLD)).thenReturn(1);
        when(users.countByProfilePicture(OLD)).thenReturn(0L);
        service.removeProfilePicture(7L);
        InOrder order = Mockito.inOrder(users, avatarStorage);
        order.verify(users).clearProfilePicture(7L, OLD);
        order.verify(avatarStorage).delete(OLD);
    }

    @Test
    void removingWhenThereIsNoPictureDoesNothing() {
        when(users.findById(7L)).thenReturn(Optional.of(userWith(null)));
        service.removeProfilePicture(7L);
        verify(users, never()).clearProfilePicture(anyLong(), anyString());
        verify(avatarStorage, never()).delete(any());
    }
}
