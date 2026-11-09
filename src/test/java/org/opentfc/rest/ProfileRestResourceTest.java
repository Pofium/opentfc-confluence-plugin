package org.opentfc.rest;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.sal.api.user.UserKey;
import net.java.ao.Query;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opentfc.ao.FilterProfile;
import org.opentfc.model.FilterProfileModel;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the profile REST resource, including the unauthenticated guards that
 * protect saves and deletes.
 */
public class ProfileRestResourceTest {

    private ActiveObjects ao;
    private ProfileRestResource resource;

    @Before
    public void setUp() {
        ao = mock(ActiveObjects.class);
        resource = new ProfileRestResource(ao);
        AuthenticatedUserThreadLocal.reset();
    }

    @After
    public void tearDown() {
        AuthenticatedUserThreadLocal.reset();
    }

    private ConfluenceUser loginAs(String userKey) {
        ConfluenceUser user = mock(ConfluenceUser.class);
        when(user.getKey()).thenReturn(new UserKey(userKey));
        AuthenticatedUserThreadLocal.set(user);
        return user;
    }

    private FilterProfile profile(long id, String userKey, String name) {
        FilterProfile fp = mock(FilterProfile.class);
        when(fp.getID()).thenReturn((int) id);
        when(fp.getPageId()).thenReturn(1L);
        when(fp.getMacroId()).thenReturn("macro");
        when(fp.getUserKey()).thenReturn(userKey);
        when(fp.getProfileName()).thenReturn(name);
        when(fp.getFilterStateJson()).thenReturn("{\"q\":\"x\"}");
        return fp;
    }

    /**
     * The resources serialize with {@link OpenTfcJson} and return JSON text, exactly as they do
     * on a real server - so the tests read the same wire format back.
     */
    private static String jsonOf(Response response) {
        assertEquals(String.class, response.getEntity().getClass());
        return (String) response.getEntity();
    }

    private static FilterProfileModel profileBody(String name, String stateJson) throws Exception {
        return OpenTfcJson.read("{\"name\":" + OpenTfcJson.write(name)
                + ",\"stateJson\":" + OpenTfcJson.write(stateJson) + "}", FilterProfileModel.class);
    }

    @Test
    public void testGetProfilesRequiresNoAuthenticationButFiltersByAnonymous() throws Exception {
        when(ao.find(eq(FilterProfile.class), any(Query.class))).thenReturn(new FilterProfile[0]);

        Response response = resource.getProfiles(1L, "macro");

        assertEquals(200, response.getStatus());
        assertTrue(OpenTfcJson.read(jsonOf(response), List.class).isEmpty());
        verify(ao).find(eq(FilterProfile.class), any(Query.class));
    }

    @Test
    public void testGetProfilesMapsEntitiesForCurrentUser() throws Exception {
        loginAs("user-1");
        FilterProfile entity = profile(5, "user-1", "Mine");
        when(ao.find(eq(FilterProfile.class), any(Query.class)))
                .thenReturn(new FilterProfile[]{entity});

        Response response = resource.getProfiles(1L, "macro");

        assertEquals(200, response.getStatus());
        FilterProfileModel[] models = OpenTfcJson.read(jsonOf(response), FilterProfileModel[].class);
        assertEquals(1, models.length);
        assertEquals(5L, models[0].getId());
        assertEquals("Mine", models[0].getName());
        assertEquals("{\"q\":\"x\"}", models[0].getStateJson());
    }

    @Test
    public void testSaveProfileCreatesNewEntity() throws Exception {
        loginAs("user-1");
        when(ao.find(eq(FilterProfile.class), any(Query.class))).thenReturn(new FilterProfile[0]);
        FilterProfile created = profile(9, "user-1", "New");
        when(ao.create(FilterProfile.class)).thenReturn(created);

        Response response = resource.saveProfile(1L, "macro", OpenTfcJson.write(profileBody("New", "{\"q\":\"y\"}")));

        assertEquals(200, response.getStatus());
        FilterProfileModel saved = OpenTfcJson.read(jsonOf(response), FilterProfileModel.class);
        assertEquals(9L, saved.getId());
        verify(created).setPageId(1L);
        verify(created).setMacroId("macro");
        verify(created).setUserKey("user-1");
        verify(created).setProfileName("New");
        verify(created).setFilterStateJson("{\"q\":\"y\"}");
        verify(created).save();
    }

    @Test
    public void testSaveProfileAcceptsLegacyStateFieldName() throws Exception {
        loginAs("user-1");
        when(ao.find(eq(FilterProfile.class), any(Query.class))).thenReturn(new FilterProfile[0]);
        FilterProfile created = profile(9, "user-1", "Legacy");
        when(ao.create(FilterProfile.class)).thenReturn(created);

        // Bodies produced by older plugin builds used "state" instead of "stateJson"
        Response response = resource.saveProfile(1L, "macro",
                "{\"name\":\"Legacy\",\"state\":\"{\\\"q\\\":\\\"old\\\"}\"}");

        assertEquals(200, response.getStatus());
        verify(created).setFilterStateJson("{\"q\":\"old\"}");
    }

    @Test
    public void testSaveProfileRejectsMalformedBody() {
        loginAs("user-1");
        Response response = resource.saveProfile(1L, "macro", "{not json");
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testSaveProfileUpdatesExistingEntity() throws Exception {
        loginAs("user-1");
        FilterProfile existing = profile(3, "user-1", "Existing");
        when(ao.find(eq(FilterProfile.class), any(Query.class))).thenReturn(new FilterProfile[]{existing});

        Response response = resource.saveProfile(1L, "macro", OpenTfcJson.write(profileBody("Existing", "{\"q\":\"z\"}")));

        assertEquals(200, response.getStatus());
        verify(existing).setFilterStateJson("{\"q\":\"z\"}");
        verify(existing).setUpdatedTime(anyLong());
        verify(existing).save();
        verify(ao, never()).create(FilterProfile.class);
    }

    @Test
    public void testSaveProfileRejectsAnonymousUser() throws Exception {
        Response response = resource.saveProfile(1L, "macro",
                OpenTfcJson.write(profileBody("x", "{}")));

        assertEquals(401, response.getStatus());
        verifyNoInteractions(ao);
    }

    @Test
    public void testDeleteProfileRejectsAnonymousUser() {
        Response response = resource.deleteProfile(1L);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(ao);
    }

    @Test
    public void testDeleteProfileRemovesOwnedProfile() {
        loginAs("user-1");
        FilterProfile owned = profile(4, "user-1", "Mine");
        when(ao.get(FilterProfile.class, 4)).thenReturn(owned);

        Response response = resource.deleteProfile(4L);

        assertEquals(204, response.getStatus());
        verify(ao).delete(owned);
    }

    @Test
    public void testDeleteProfileRejectsForeignProfile() {
        loginAs("user-1");
        FilterProfile foreign = profile(4, "user-2", "Not mine");
        when(ao.get(FilterProfile.class, 4)).thenReturn(foreign);

        Response response = resource.deleteProfile(4L);

        assertEquals(404, response.getStatus());
        verify(ao, never()).delete(any(FilterProfile.class));
    }

    @Test
    public void testDeleteProfileMissingEntity() {
        loginAs("user-1");
        when(ao.get(FilterProfile.class, 99)).thenReturn(null);

        Response response = resource.deleteProfile(99L);

        assertEquals(404, response.getStatus());
        verify(ao, never()).delete(any(FilterProfile.class));
    }
}
