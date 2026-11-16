package com.clothmarket.service;

import com.clothmarket.dto.AdminUserUpdateRequest;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.AdminAuditLog;
import com.clothmarket.model.Post;
import com.clothmarket.model.User;
import com.clothmarket.repository.PostRepository;
import com.clothmarket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminPanelServiceTest {

    private static final long ADMIN_ID = 1L;
    private static final long OTHER_ADMIN_ID = 2L;
    private static final long USER_ID = 10L;

    private PostRepository posts;
    private UserRepository users;
    private PostService postService;
    private AccountDeletionService accountDeletion;
    private AdminAccess adminAccess;
    private AdminAuditService audit;
    private AdminPanelService service;

    @BeforeEach
    void setUp() {
        posts = mock(PostRepository.class);
        users = mock(UserRepository.class);
        postService = mock(PostService.class);
        accountDeletion = mock(AccountDeletionService.class);
        adminAccess = mock(AdminAccess.class);
        audit = mock(AdminAuditService.class);
        service = new AdminPanelService(posts, users, postService, accountDeletion, adminAccess, audit);

        when(adminAccess.isSuperAdmin(ADMIN_ID)).thenReturn(true);
        when(adminAccess.isSuperAdmin(OTHER_ADMIN_ID)).thenReturn(true);
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setName("Asha");
        u.setLocation("Pune");
        u.setMobile("9876543210");
        u.setEmail("asha@example.com");
        u.setPassword("hash-do-not-change");
        u.setBlocked(false);
        when(users.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private static AdminUserUpdateRequest request(String name, String location, String email, String mobile) {
        AdminUserUpdateRequest r = mock(AdminUserUpdateRequest.class);
        when(r.getName()).thenReturn(name);
        when(r.getLocation()).thenReturn(location);
        when(r.getEmail()).thenReturn(email);
        when(r.getMobile()).thenReturn(mobile);
        return r;
    }

    private static ApiException assertStatus(HttpStatus expected, Runnable action) {
        ApiException e = assertThrows(ApiException.class, action::run);
        assertEquals(expected, e.getStatus());
        return e;
    }

    // ------------------------------------------------------------ delete user

    @Test
    void deletingASuperAdminIsRefusedWith409AndNothingIsDeleted() {
        user(OTHER_ADMIN_ID);
        assertStatus(HttpStatus.CONFLICT, () -> service.deleteUser(ADMIN_ID, OTHER_ADMIN_ID, null));
        verify(accountDeletion, never()).deleteAccountAsModerator(any());
        verifyNoInteractions(audit);
    }

    @Test
    void anAdminCannotDeleteTheirOwnAccount() {
        user(ADMIN_ID);
        assertStatus(HttpStatus.CONFLICT, () -> service.deleteUser(ADMIN_ID, ADMIN_ID, null));
        verify(accountDeletion, never()).deleteAccountAsModerator(any());
    }

    @Test
    void deletingAnOrdinaryUserDeletesThemAndWritesAnAuditRecord() {
        user(USER_ID);
        service.deleteUser(ADMIN_ID, USER_ID, "spam");
        verify(accountDeletion).deleteAccountAsModerator(USER_ID);
        verify(audit).record(ADMIN_ID, AdminAuditLog.USER_DELETE, "USER", USER_ID, "spam", null);
    }

    @Test
    void deletingAMissingUserIs404() {
        when(users.findById(99L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deleteUser(ADMIN_ID, 99L, null));
        verify(accountDeletion, never()).deleteAccountAsModerator(any());
    }

    // ------------------------------------------------------------ block / unblock

    @Test
    void blockingASuperAdminIsRefused() {
        user(OTHER_ADMIN_ID);
        assertStatus(HttpStatus.CONFLICT, () -> service.blockUser(ADMIN_ID, OTHER_ADMIN_ID, null));
        verify(users, never()).save(any());
    }

    @Test
    void anAdminCannotBlockThemselves() {
        user(ADMIN_ID);
        assertStatus(HttpStatus.CONFLICT, () -> service.blockUser(ADMIN_ID, ADMIN_ID, null));
        verify(users, never()).save(any());
    }

    @Test
    void blockingSetsTheFlagAndIsAudited() {
        User u = user(USER_ID);
        service.blockUser(ADMIN_ID, USER_ID, "abuse");
        assertTrue(u.getBlocked());
        verify(users).save(u);
        verify(audit).record(ADMIN_ID, AdminAuditLog.USER_BLOCK, "USER", USER_ID, "abuse", null);
    }

    @Test
    void blockingAnAlreadyBlockedUserChangesNothingAndIsNotAudited() {
        User u = user(USER_ID);
        u.setBlocked(true);
        service.blockUser(ADMIN_ID, USER_ID, null);
        verify(users, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void unblockingClearsTheFlagAndIsAudited() {
        User u = user(USER_ID);
        u.setBlocked(true);
        service.unblockUser(ADMIN_ID, USER_ID);
        assertFalse(u.getBlocked());
        verify(audit).record(ADMIN_ID, AdminAuditLog.USER_UNBLOCK, "USER", USER_ID, null, null);
    }

    @Test
    void unblockingAUserWhoIsNotBlockedChangesNothing() {
        user(USER_ID);
        service.unblockUser(ADMIN_ID, USER_ID);
        verify(users, never()).save(any());
        verifyNoInteractions(audit);
    }

    // ------------------------------------------------------------ edit user

    @Test
    void editingNameAndLocationLeavesEmailMobilePasswordAndBlockedUntouched() {
        User u = user(USER_ID);
        assertNotNull(service.updateUser(ADMIN_ID, USER_ID, request("  Asha Rao ", " Mumbai  ", "", "")));

        assertEquals("Asha Rao", u.getName());
        assertEquals("Mumbai", u.getLocation());
        assertEquals("asha@example.com", u.getEmail());
        assertEquals("9876543210", u.getMobile());
        assertEquals("hash-do-not-change", u.getPassword());
        assertFalse(u.getBlocked());
        verify(audit).record(ADMIN_ID, AdminAuditLog.USER_UPDATE, "USER", USER_ID, null, "fields=name,location");
        verify(users, never()).existsByEmail(any());
        verify(users, never()).existsByMobile(any());
    }

    @Test
    void blankEmailAndMobileMeanUnchangedAndNullIsTreatedTheSame() {
        User u = user(USER_ID);
        service.updateUser(ADMIN_ID, USER_ID, request("Asha Rao", "Pune", null, null));
        assertEquals("asha@example.com", u.getEmail());
        assertEquals("9876543210", u.getMobile());
    }

    @Test
    void changingEmailNormalisesItAndAuditsOnlyTheFieldName() {
        User u = user(USER_ID);
        service.updateUser(ADMIN_ID, USER_ID, request("Asha", "Pune", "  New.Name@Example.COM ", ""));
        assertEquals("new.name@example.com", u.getEmail());
        verify(users).existsByEmail("new.name@example.com");
        verify(audit).record(ADMIN_ID, AdminAuditLog.USER_UPDATE, "USER", USER_ID, null, "fields=email");
    }

    @Test
    void changingMobileIsAuditedAsAFieldName() {
        User u = user(USER_ID);
        service.updateUser(ADMIN_ID, USER_ID, request("Asha", "Pune", "", "9123456780"));
        assertEquals("9123456780", u.getMobile());
        verify(audit).record(ADMIN_ID, AdminAuditLog.USER_UPDATE, "USER", USER_ID, null, "fields=mobile");
    }

    @Test
    void anEmailUsedByAnotherAccountIsRefusedWith409AndNothingChanges() {
        User u = user(USER_ID);
        when(users.existsByEmail("taken@example.com")).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT,
                () -> service.updateUser(ADMIN_ID, USER_ID, request("Changed", "Pune", "taken@example.com", "")));
        assertEquals("Asha", u.getName()); // not even the name was touched
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void aMobileUsedByAnotherAccountIsRefusedWith409() {
        user(USER_ID);
        when(users.existsByMobile("9123456780")).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT,
                () -> service.updateUser(ADMIN_ID, USER_ID, request("Asha", "Pune", "", "9123456780")));
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void anInvalidEmailIsRefusedWith400() {
        user(USER_ID);
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.updateUser(ADMIN_ID, USER_ID, request("Asha", "Pune", "not-an-email", "")));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void anInvalidMobileIsRefusedWith400() {
        user(USER_ID);
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.updateUser(ADMIN_ID, USER_ID, request("Asha", "Pune", "", "12ab")));
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.updateUser(ADMIN_ID, USER_ID, request("Asha", "Pune", "", "12345")));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void aUniqueIndexClashAtSaveTimeBecomesA409() {
        user(USER_ID);
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertStatus(HttpStatus.CONFLICT,
                () -> service.updateUser(ADMIN_ID, USER_ID, request("Asha", "Pune", "new@example.com", "")));
        verifyNoInteractions(audit);
    }

    @Test
    void savingWithNothingChangedWritesNothingAndAuditsNothing() {
        user(USER_ID);
        assertNotNull(service.updateUser(ADMIN_ID, USER_ID, request("Asha", "Pune", "ASHA@example.com", "9876543210")));
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void anotherSuperAdminsEmailAndMobileCannotBeChanged() {
        user(OTHER_ADMIN_ID);
        assertStatus(HttpStatus.CONFLICT,
                () -> service.updateUser(ADMIN_ID, OTHER_ADMIN_ID, request("Asha", "Pune", "takeover@example.com", "")));
        assertStatus(HttpStatus.CONFLICT,
                () -> service.updateUser(ADMIN_ID, OTHER_ADMIN_ID, request("Asha", "Pune", "", "9123456780")));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void anotherSuperAdminsNameAndLocationCanStillBeEdited() {
        User u = user(OTHER_ADMIN_ID);
        service.updateUser(ADMIN_ID, OTHER_ADMIN_ID, request("New Name", "Pune", "", ""));
        assertEquals("New Name", u.getName());
    }

    @Test
    void anAdminMayChangeTheirOwnEmail() {
        User u = user(ADMIN_ID);
        service.updateUser(ADMIN_ID, ADMIN_ID, request("Asha", "Pune", "me@example.com", ""));
        assertEquals("me@example.com", u.getEmail());
    }

    // ------------------------------------------------------------ delete post

    @Test
    void deletingAPostRemovesItAndIsAuditedWithoutPersonalData() {
        Post p = new Post();
        p.setId(5L);
        p.setUserId(USER_ID);
        p.setMediaType("image");
        when(posts.findByIdWithOwner(5L)).thenReturn(Optional.of(p));

        service.deletePost(ADMIN_ID, 5L, "stolen photo");

        verify(postService).removePostAsModerator(5L);
        verify(audit).record(ADMIN_ID, AdminAuditLog.POST_DELETE, "POST", 5L, "stolen photo",
                "ownerId=10 mediaType=image");
    }

    @Test
    void deletingAMissingPostIs404AndNothingIsRemoved() {
        when(posts.findByIdWithOwner(404L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.deletePost(ADMIN_ID, 404L, null));
        verify(postService, never()).removePostAsModerator(any());
        verifyNoInteractions(audit);
    }

    // ------------------------------------------------------------ paging

    @Test
    void pageSizeIsClampedAndNegativePagesBecomeZero() {
        when(posts.searchForAdmin(anyString(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        service.listPosts("", -3, 1000);
        service.listPosts("", 2, 0);

        verify(posts, times(2)).searchForAdmin(anyString(), captor.capture());
        assertEquals(0, captor.getAllValues().get(0).getPageNumber());
        assertEquals(50, captor.getAllValues().get(0).getPageSize());
        assertEquals(2, captor.getAllValues().get(1).getPageNumber());
        assertEquals(1, captor.getAllValues().get(1).getPageSize());
    }

    // ------------------------------------------------------------ search text

    @Test
    void emptySearchMatchesEverything() {
        assertEquals("%", AdminPanelService.searchPattern(null));
        assertEquals("%", AdminPanelService.searchPattern(""));
        assertEquals("%", AdminPanelService.searchPattern("   "));
    }

    @Test
    void searchIsTrimmedAndLowerCased() {
        assertEquals("%alice%", AdminPanelService.searchPattern("  Alice "));
    }

    @Test
    void likeWildcardsAndEscapeCharactersAreRemoved() {
        assertEquals("%50off%", AdminPanelService.searchPattern("50%_off\\"));
        assertEquals("%", AdminPanelService.searchPattern("%%__"));
    }

    @Test
    void veryLongSearchTextIsCutTo100Characters() {
        String pattern = AdminPanelService.searchPattern("a".repeat(500));
        assertEquals(102, pattern.length()); // 100 characters plus the two % signs
    }
}