package org.tkit.onecx.user.profile.rs.internal.controllers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.tkit.onecx.user.profile.domain.daos.PreferenceDAO;
import org.tkit.onecx.user.profile.domain.daos.UserProfileDAO;
import org.tkit.onecx.user.profile.domain.models.UserProfile;
import org.tkit.onecx.user.profile.domain.service.UserProfileService;
import org.tkit.onecx.user.profile.rs.internal.mappers.InternalExceptionMapper;
import org.tkit.onecx.user.profile.rs.internal.mappers.PreferenceMapper;
import org.tkit.onecx.user.profile.rs.internal.mappers.UserProfileMapper;
import org.tkit.quarkus.context.ApplicationContext;
import org.tkit.quarkus.jpa.exceptions.ConstraintException;
import org.tkit.quarkus.log.cdi.LogService;

import gen.org.tkit.onecx.user.profile.rs.internal.UserProfileApi;
import gen.org.tkit.onecx.user.profile.rs.internal.model.*;

@LogService
@ApplicationScoped
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public class UserProfileRestController implements UserProfileApi {

    @Inject
    UserProfileDAO userProfileDAO;

    @Inject
    PreferenceDAO preferenceDAO;

    @Inject
    UserProfileMapper userProfileMapper;

    @Inject
    PreferenceMapper preferenceMapper;

    @Inject
    UserProfileService jwtService;

    @Inject
    InternalExceptionMapper exceptionMapper;

    @Context
    UriInfo uriInfo;

    @Override
    public Response createUserProfilePreference(CreateUserPreferenceDTO createUserPreferenceDTO) {
        var userId = ApplicationContext.get().getPrincipal();
        var userProfile = userProfileDAO.getUserProfileByUserId(userId, UserProfile.ENTITY_GRAPH_LOAD_ALL);

        if (userProfile == null) {
            throw new ConstraintException("Profile for userID: " + userId + " does not exist",
                    ErrorKeys.USER_PROFILE_DOES_NOT_EXIST, null);
        }
        var gotPreference = preferenceMapper.create(createUserPreferenceDTO);
        gotPreference.setUserProfile(userProfile);

        var createdPreference = preferenceDAO.create(gotPreference);
        var createdPreferenceDTOv1 = preferenceMapper.map(createdPreference);

        return Response
                .created(uriInfo.getAbsolutePathBuilder().path(createdPreference.getId()).build())
                .entity(createdPreferenceDTOv1)
                .build();
    }

    @Override
    @Transactional
    public Response deleteMyUserProfile() {
        var userId = ApplicationContext.get().getPrincipal();

        var userProfile = userProfileDAO.getUserProfileByUserId(userId, UserProfile.ENTITY_GRAPH_LOAD_ALL);

        if (userProfile != null) {
            var preferences = preferenceDAO.getAllPreferencesByUserId(userId);
            preferenceDAO.delete(preferences);
            userProfileDAO.delete(userProfile);

        }

        return Response.noContent().build();
    }

    @Override
    @Transactional
    public Response deleteUserProfilePreference(String id) {
        var userId = ApplicationContext.get().getPrincipal();

        var preference = preferenceDAO.findById(id);

        if (preference != null) {
            if (!preference.getUserProfile().getUserId().equals(userId)) {
                throw new ConstraintException("Preference is not from actual user", ErrorKeys.PREFERENCE_NOT_FROM_ACTUAL_USER,
                        null);
            } else {
                preferenceDAO.delete(preference);
            }
        }

        return Response.noContent().build();
    }

    @Override
    public Response getMyUserProfile() {
        var userId = ApplicationContext.get().getPrincipal();

        var userProfile = userProfileDAO.getUserProfileByUserId(userId, UserProfile.ENTITY_GRAPH_LOAD_PERSON);

        if (userProfile == null) {
            // create user profile if it does not exist
            var createUserProfile = jwtService.createProfileFromToken();
            userProfile = userProfileDAO.create(createUserProfile);
        }

        return Response.ok(userProfileMapper.mapProfile(userProfile)).build();
    }

    @Override
    public Response getUserProfilePerson() {
        var userId = ApplicationContext.get().getPrincipal();

        var userProfile = userProfileDAO.getUserProfileByUserId(userId, UserProfile.ENTITY_GRAPH_LOAD_PERSON);

        if (userProfile == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        var userPerson = userProfileMapper.mapUserPerson(userProfile);

        return Response.ok(userPerson).build();
    }

    @Override
    public Response getUserProfilePreference() {
        var userId = ApplicationContext.get().getPrincipal();

        var preferences = preferenceDAO.getAllPreferencesByUserId(userId);

        return Response.ok(preferenceMapper.find(preferences)).build();
    }

    @Override
    public Response getUserProfileSettings() {
        var userId = ApplicationContext.get().getPrincipal();

        var userProfile = userProfileDAO.getUserProfileByUserId(userId, UserProfile.ENTITY_GRAPH_LOAD_PERSON);

        if (userProfile == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(userProfileMapper.mapAccountSettings(userProfile)).build();
    }

    @Override
    public Response updateUserProfilePerson(UpdateUserPersonRequestDTO updateUserPersonDTO) {
        var userId = ApplicationContext.get().getPrincipal();

        var userProfile = userProfileDAO.getUserProfileByUserId(userId, UserProfile.ENTITY_GRAPH_LOAD_PERSON);
        if (userProfile == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        userProfileMapper.updateUserPerson(userProfile, updateUserPersonDTO);
        userProfile = userProfileDAO.update(userProfile);

        return Response.ok(userProfileMapper.mapUserPerson(userProfile)).build();
    }

    @Override
    public Response updateUserProfilePreference(String id, String body) {
        if (StringUtils.isEmpty(body)) {
            throw new ConstraintException("Cannot update user preference with empty body",
                    ErrorKeys.PREFERENCE_WITH_EMPTY_VALUE,
                    null);
        }
        var userId = ApplicationContext.get().getPrincipal();

        var preference = preferenceDAO.findById(id);
        if (preference == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!preference.getUserProfile().getUserId().equals(userId)) {
            throw new ConstraintException("User does not match preference user id", ErrorKeys.PREFERENCE_NOT_FROM_ACTUAL_USER,
                    null);
        }
        preference.setValue(body);
        preference = preferenceDAO.update(preference);

        return Response.ok(preferenceMapper.map(preference)).build();
    }

    @Override
    public Response updateUserProfileSettings(UpdateUserSettingsDTO updateUserSettingsDTO) {
        var userId = ApplicationContext.get().getPrincipal();

        var userProfile = userProfileDAO.getUserProfileByUserId(userId, UserProfile.ENTITY_GRAPH_LOAD_PERSON);
        if (userProfile == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        userProfileMapper.updateUserSettings(userProfile, updateUserSettingsDTO);
        userProfile = userProfileDAO.update(userProfile);

        return Response.ok(userProfileMapper.mapAccountSettings(userProfile)).build();
    }

    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> daoException(OptimisticLockException ex) {
        return exceptionMapper.optimisticLock(ex);
    }

    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> constraintViolation(ConstraintViolationException ex) {
        return exceptionMapper.constraint(ex);
    }

    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> constraintException(ConstraintException ex) {
        return exceptionMapper.exception(ex);
    }

    enum ErrorKeys {
        USER_PROFILE_DOES_NOT_EXIST,

        PREFERENCE_NOT_FROM_ACTUAL_USER,

        PREFERENCE_WITH_EMPTY_VALUE,

    }
}
