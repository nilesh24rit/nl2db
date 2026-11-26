package com.nl2db.gateway;

import com.nl2db.auth.*;
import com.nl2db.security.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.*;

/**
 * Cross-tenant security integration tests using PostgreSQL Testcontainers.
 * Verifies that users can only access resources belonging to organizations
 * they are active members of, and that forged/organization IDs from other
 * organizations are rejected.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CrossTenantSecurityTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("nl2db")
                    .withUsername("sa")
                    .withPassword("");

    @Container
    static PostgreSQLContainer<?> postgresContainer = POSTGRES;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private OrganizationInvitationRepository invitationRepository;

    private UUID jwtTokenOwnerA;
    private UUID jwtTokenAdminA;
    private UUID jwtTokenMemberA;
    private UUID jwtTokenMemberB;
    private UUID orgAId;
    private UUID orgBId;

    @BeforeAll
    static void setUpContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void tearDownContainer() {
        POSTGRES.stop();
    }

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        invitationRepository.deleteAll();
        organizationRepository.deleteAll();

        JWTTokenProvider jwtProvider = new JWTTokenProvider();

        orgAId = UUID.randomUUID();
        UUID ownerA = UUID.randomUUID();
        UUID adminA = UUID.randomUUID();
        UUID memberA = UUID.randomUUID();
        UUID memberB = UUID.randomUUID();

        Organization orgA = new Organization(orgAId, "Organization A", "org-a", OrganizationStatus.ACTIVE, ownerA, Instant.now());
        organizationRepository.save(orgA);

        orgBId = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        Organization orgB = new Organization(orgBId, "Organization B", "org-b", OrganizationStatus.ACTIVE, ownerB, Instant.now());
        organizationRepository.save(orgB);

        // Organization A memberships
        OrganizationMembership ownerAMem = new OrganizationMembership(UUID.randomUUID(), ownerA, orgAId, "OWNER", "ACTIVE", Instant.now());
        membershipRepository.save(ownerAMem);
        OrganizationMembership adminAMem = new OrganizationMembership(UUID.randomUUID(), adminA, orgAId, "ADMIN", "ACTIVE", Instant.now());
        membershipRepository.save(adminAMem);
        OrganizationMembership memberAMem = new OrganizationMembership(UUID.randomUUID(), memberA, orgAId, "MEMBER", "ACTIVE", Instant.now());
        membershipRepository.save(memberAMem);
        OrganizationMembership memberBMem = new OrganizationMembership(UUID.randomUUID(), memberB, orgAId, "MEMBER", "ACTIVE", Instant.now());
        membershipRepository.save(memberBMem);

        // Organization B memberships
        OrganizationMembership ownerBMem = new OrganizationMembership(UUID.randomUUID(), ownerB, orgBId, "OWNER", "ACTIVE", Instant.now());
        membershipRepository.save(ownerBMem);
        OrganizationMembership adminBMem = new OrganizationMembership(UUID.randomUUID(), adminB, orgBId, "ADMIN", "ACTIVE", Instant.now());
        membershipRepository.save(adminBMem);
        OrganizationMembership memberBOrgBMem = new OrganizationMembership(UUID.randomUUID(), memberB, orgBId, "MEMBER", "ACTIVE", Instant.now());
        membershipRepository.save(memberBOrgBMem);

        jwtTokenOwnerA = jwtProvider.createAccessToken(ownerA);
        jwtTokenAdminA = jwtProvider.createAccessToken(adminA);
        jwtTokenMemberA = jwtProvider.createAccessToken(memberA);
        jwtTokenMemberB = jwtProvider.createAccessToken(memberB);

        // Invitations
        String tokenHashA = hashToken("token-a-1");
        OrganizationInvitation invA = new OrganizationInvitation(UUID.randomUUID(), orgAId, "invitee-a@example.com", tokenHashA, "PENDING", ownerA, Instant.now(), Instant.now().plusDays(7));
        invitationRepository.save(invA);

        String tokenHashB = hashToken("token-b-1");
        OrganizationInvitation invB = new OrganizationInvitation(UUID.randomUUID(), orgBId, "invitee-b@example.com", tokenHashB, "PENDING", ownerB, Instant.now(), Instant.now().plusDays(7));
        invitationRepository.save(invB);
    }

    @Test
    void ownerCannotAccessOtherOrganizationResources() {
        MockMvc mockMvc = MockMvcBuilders.standardSetup(new org.springframework.web.servlet.config.annotation.WebMvcConfigurer()).build();
        MockHttpServletRequestBuilder orgBGet = get("/api/v1/organizations/{orgId}", orgBId).header("Authorization", "Bearer " + jwtTokenOwnerA);
        MvcResult result = mockMvc.perform(orgBGet).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Owner cannot access Org B resources - got 403");

        MockHttpServletRequestBuilder orgBMembers = get("/api/v1/organizations/{orgId}/members", orgBId).header("Authorization", "Bearer " + jwtTokenOwnerA);
        result = mockMvc.perform(orgBMembers).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Owner cannot list Org B members - got 403");
    }

    @Test
    void adminCannotModifyOtherOrganizationMemberships() {
        MockHttpServletRequestBuilder orgBRoleUpdate = patch("/api/v1/organizations/{orgId}/members/{targetUserId}/role", orgBId, memberB)
                .contentType(MediaType.APPLICATION_JSON)
                .param("currentUserId", adminA.toString())
                .header("Authorization", "Bearer " + jwtTokenAdminA);
        MvcResult result = mockMvc.perform(orgBRoleUpdate).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Admin cannot modify Org B memberships - got 403");

        MockHttpServletRequestBuilder orgBInviteCreate = post("/api/v1/organizations/{orgId}/invitations", orgBId)
                .contentType(MediaType.APPLICATION_JSON)
                .param("email", "test@example.com")
                .param("currentUserId", adminA.toString())
                .header("Authorization", "Bearer " + jwtTokenAdminA);
        result = mockMvc.perform(orgBInviteCreate).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Admin cannot create invitations in Org B - got 403");
    }

    @Test
    void memberCannotPerformOwnerAdminOperations() {
        MockHttpServletRequestBuilder orgAUpdate = patch("/api/v1/organizations/{orgId}", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .param("newName", "Modified Org A")
                .header("Authorization", "Bearer " + jwtTokenMemberA);
        MvcResult result = mockMvc.perform(orgAUpdate).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Member cannot update organization - got 403");

        MockHttpServletRequestBuilder orgAMemberRoleUpdate = patch("/api/v1/organizations/{orgId}/members/{targetUserId}/role", orgAId, memberB)
                .contentType(MediaType.APPLICATION_JSON)
                .param("currentUserId", memberA.toString())
                .header("Authorization", "Bearer " + jwtTokenMemberA);
        result = mockMvc.perform(orgAMemberRoleUpdate).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Member cannot update member role - got 403");

        MockHttpServletRequestBuilder orgAMemberRemove = delete("/api/v1/organizations/{orgId}/members/{targetUserId}", orgAId, memberB)
                .contentType(MediaType.APPLICATION_JSON)
                .param("currentUserId", memberA.toString());
        result = mockMvc.perform(orgAMemberRemove).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Member cannot remove other members - got 403");
    }

    @Test
    void forgedOrganizationIdDoesNotBypassTenantChecks() {
        UUID forgedOrgId = UUID.randomUUID();
        MockHttpServletRequestBuilder forgedGet = get("/api/v1/organizations/{orgId}", forgedOrgId).header("Authorization", "Bearer " + jwtTokenOwnerA);
        MvcResult result = mockMvc.perform(forgedGet).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Forged org ID should be denied - got 403");

        MockHttpServletRequestBuilder forgedBodyPatch = patch("/api/v1/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\": \"Forged\", \"id\": \"" + forgedOrgId + "\", \"slug\": \"forged\"}")
                .header("Authorization", "Bearer " + jwtTokenOwnerA);
        result = mockMvc.perform(forgedBodyPatch).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Forged org ID in body should be denied - got 403");
    }

    @Test
    void resourceIdBelongingToAnotherOrganizationCannotBeUsedForIdor() {
        MockHttpServletRequestBuilder orgBMembersByAId = get("/api/v1/organizations/{orgId}/members", orgAId).header("Authorization", "Bearer " + jwtTokenOwnerA);
        MvcResult result = mockMvc.perform(orgBMembersByAId).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Cannot list Org A members with Org B ID - got 403");

        MockHttpServletRequestBuilder orgBUpdateByA = patch("/api/v1/organizations/{orgId}", orgBId)
                .contentType(MediaType.APPLICATION_JSON)
                .param("userId", ownerA.toString())
                .param("newName", "Forged Name")
                .header("Authorization", "Bearer " + jwtTokenOwnerA);
        result = mockMvc.perform(orgBUpdateByA).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Cannot update Org B with Org A user - got 403");
    }

    @Test
    void suspendedMemberLosesAccess() {
        var membershipOpt = membershipRepository.findByUserIdAndOrganizationId(memberA, orgAId);
        if (membershipOpt.isPresent()) {
            OrganizationMembership mem = membershipOpt.get();
            mem = new OrganizationMembership(mem.id(), mem.userId(), mem.organizationId(), mem.role(), "SUSPENDED", mem.joinedAt());
            membershipRepository.save(mem);
        }

        MockHttpServletRequestBuilder suspendedAccess = get("/api/v1/organizations/{orgId}", orgAId).header("Authorization", "Bearer " + jwtTokenMemberA);
        MvcResult result = mockMvc.perform(suspendedAccess).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Suspended member cannot access organization - got 403");

        MockHttpServletRequestBuilder suspendedMembers = get("/api/v1/organizations/{orgId}/members", orgAId).header("Authorization", "Bearer " + jwtTokenMemberA);
        result = mockMvc.perform(suspendedMembers).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Suspended member cannot list members - got 403");
    }

    @Test
    void removedMemberLosesAccess() {
        var membershipOpt = membershipRepository.findByUserIdAndOrganizationId(memberA, orgAId);
        if (membershipOpt.isPresent()) {
            OrganizationMembership mem = membershipOpt.get();
            mem = new OrganizationMembership(mem.id(), mem.userId(), mem.organizationId(), mem.role(), "REMOVED", mem.joinedAt());
            membershipRepository.save(mem);
        }

        MockHttpServletRequestBuilder removedAccess = get("/api/v1/organizations/{orgId}", orgAId).header("Authorization", "Bearer " + jwtTokenMemberA);
        MvcResult result = mockMvc.perform(removedAccess).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Removed member cannot access organization - got 403");
    }

    @Test
    void revokedMembershipCannotBeBypassedWithValidJwt() {
        var membershipOpt = membershipRepository.findByUserIdAndOrganizationId(memberA, orgAId);
        if (membershipOpt.isPresent()) {
            OrganizationMembership mem = membershipOpt.get();
            mem = new OrganizationMembership(mem.id(), mem.userId(), mem.organizationId(), mem.role(), "REMOVED", mem.joinedAt());
            membershipRepository.save(mem);
        }

        MockHttpServletRequestBuilder revokedAccess = get("/api/v1/organizations/{orgId}", orgAId).header("Authorization", "Bearer " + jwtTokenMemberA);
        MvcResult result = mockMvc.perform(revokedAccess).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Revoked membership cannot be bypassed with valid JWT - got 403");
    }

    @Test
    void userCannotImpersonateAnotherPrincipalOrOrganization() {
        MockHttpServletRequestBuilder orgBImpersonation = get("/api/v1/organizations/{orgId}", orgBId).header("Authorization", "Bearer " + jwtTokenOwnerA);
        MvcResult result = mockMvc.perform(orgBImpersonation).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "User cannot impersonate access to Org B - got 403");

        MockHttpServletRequestBuilder otherUserToken = get("/api/v1/organizations/{orgId}", orgAId).header("Authorization", "Bearer " + jwtTokenMemberB);
        result = mockMvc.perform(otherUserToken).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "User cannot use another's JWT token - got 403");
    }

    @Test
    void invitationOperationsCannotCrossOrganizationBoundaries() {
        MockHttpServletRequestBuilder orgBInviteCreate = post("/api/v1/organizations/{orgId}/invitations", orgBId)
                .contentType(MediaType.APPLICATION_JSON)
                .param("email", "test@example.com")
                .param("currentUserId", adminA.toString())
                .header("Authorization", "Bearer " + jwtTokenAdminA);
        MvcResult result = mockMvc.perform(orgBInviteCreate).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Admin cannot create invitations in Org B - got 403");

        MockHttpServletRequestBuilder orgBInviteList = get("/api/v1/organizations/{orgId}/invitations", orgBId).header("Authorization", "Bearer " + jwtTokenMemberA);
        result = mockMvc.perform(orgBInviteList).andExpect(status().isForbidden()).andReturn();
        assertTrue(true, "Member cannot list invitations in Org B - got 403");
    }

    @Test
    void legitimateMembersRetainAccess() {
        MockHttpServletRequestBuilder ownerAccess = get("/api/v1/organizations/{orgId}", orgAId).header("Authorization", "Bearer " + jwtTokenOwnerA);
        MvcResult result = mockMvc.perform(ownerAccess).andExpect(status().isOk()).andReturn();
        assertTrue(true, "Owner should have access to their organization - got 200");

        MockHttpServletRequestBuilder adminPatch = patch("/api/v1/organizations/{orgId}", orgAId)
                .contentType(MediaType.APPLICATION_JSON)
                .param("newName", "Modified Org A")
                .param("userId", ownerA.toString())
                .header("Authorization", "Bearer " + jwtTokenAdminA);
        result = mockMvc.perform(adminPatch).andExpect(status().isOk()).andReturn();
        assertTrue(true, "Admin should be able to update organization - got 200");

        MockHttpServletRequestBuilder memberGet = get("/api/v1/organizations/{orgId}", orgAId).header("Authorization", "Bearer " + jwtTokenMemberA);
        result = mockMvc.perform(memberGet).andExpect(status().isOk()).andReturn();
        assertTrue(true, "Member should have read access - got 200");

        MockHttpServletRequestBuilder memberMembers = get("/api/v1/organizations/{orgId}/members", orgAId).header("Authorization", "Bearer " + jwtTokenMemberA);
        result = mockMvc.perform(memberMembers).andExpect(status().isOk()).andReturn();
        assertTrue(true, "Member should be able to list members - got 200");
    }

    private static String hashToken(String token) {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}