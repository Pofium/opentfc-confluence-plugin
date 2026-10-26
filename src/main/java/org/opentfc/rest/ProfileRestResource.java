package org.opentfc.rest;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import net.java.ao.Query;
import org.opentfc.ao.FilterProfile;
import org.opentfc.model.FilterProfileModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * REST endpoint for saving, retrieving, and deleting user table filter profiles.
 */
@Path("/profile")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileRestResource {
    private static final Logger log = LoggerFactory.getLogger(ProfileRestResource.class);

    private final ActiveObjects ao;

    public ProfileRestResource(@ComponentImport ActiveObjects ao) {
        this.ao = ao;
    }

    @GET
    @Path("/{pageId}/{macroId}")
    public Response getProfiles(@PathParam("pageId") long pageId, @PathParam("macroId") String macroId) {
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        String userKey = user != null ? user.getKey().getStringValue() : "anonymous";

        FilterProfile[] profiles = ao.find(FilterProfile.class,
                Query.select().where("PAGE_ID = ? AND MACRO_ID = ? AND USER_KEY = ?", pageId, macroId, userKey));

        List<FilterProfileModel> result = new ArrayList<>();
        for (FilterProfile fp : profiles) {
            result.add(new FilterProfileModel(fp.getID(), fp.getPageId(), fp.getMacroId(), fp.getProfileName(), fp.getFilterStateJson()));
        }
        return Response.ok(result).build();
    }

    @POST
    @Path("/{pageId}/{macroId}")
    public Response saveProfile(@PathParam("pageId") long pageId,
                                @PathParam("macroId") String macroId,
                                FilterProfileModel model) {
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        String userKey = user.getKey().getStringValue();

        FilterProfile[] existing = ao.find(FilterProfile.class,
                Query.select().where("PAGE_ID = ? AND MACRO_ID = ? AND USER_KEY = ? AND PROFILE_NAME = ?",
                        pageId, macroId, userKey, model.getName()));

        FilterProfile profile;
        long now = System.currentTimeMillis();
        if (existing.length > 0) {
            profile = existing[0];
            profile.setFilterStateJson(model.getStateJson());
            profile.setUpdatedTime(now);
            profile.save();
        } else {
            profile = ao.create(FilterProfile.class);
            profile.setPageId(pageId);
            profile.setMacroId(macroId);
            profile.setUserKey(userKey);
            profile.setProfileName(model.getName());
            profile.setFilterStateJson(model.getStateJson());
            profile.setCreatedTime(now);
            profile.setUpdatedTime(now);
            profile.save();
        }

        return Response.ok(new FilterProfileModel(profile.getID(), profile.getPageId(), profile.getMacroId(), profile.getProfileName(), profile.getFilterStateJson())).build();
    }

    @DELETE
    @Path("/{profileId}")
    public Response deleteProfile(@PathParam("profileId") long profileId) {
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        FilterProfile profile = ao.get(FilterProfile.class, profileId);
        if (profile != null && profile.getUserKey().equals(user.getKey().getStringValue())) {
            ao.delete(profile);
            return Response.noContent().build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }
}
