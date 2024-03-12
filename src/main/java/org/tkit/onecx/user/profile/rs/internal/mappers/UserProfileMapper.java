package org.tkit.onecx.user.profile.rs.internal.mappers;

import java.util.List;
import java.util.stream.Stream;

import org.mapstruct.*;
import org.tkit.onecx.user.profile.domain.criteria.UserPersonCriteria;
import org.tkit.onecx.user.profile.domain.models.*;
import org.tkit.quarkus.jpa.daos.PageResult;
import org.tkit.quarkus.rs.mappers.OffsetDateTimeMapper;

import gen.org.tkit.onecx.user.profile.rs.internal.model.*;

@Mapper(uses = { OffsetDateTimeMapper.class })
public interface UserProfileMapper {

    default UserProfile create(CreateUserProfileRequestDTO dto) {
        var userProfile = new UserProfile();

        userProfile.setOrganization(dto.getOrganization());
        userProfile.setUserId(dto.getUserId());
        userProfile.setIdentityProvider(dto.getIdentityProvider());
        userProfile.setIdentityProviderId(dto.getIdentityProviderId());

        userProfile.setAccountSettings(new UserProfileAccountSettings());

        userProfile.setPerson(create(dto.getPerson()));

        return userProfile;
    }

    @Mapping(target = "phone", ignore = true)
    @Mapping(target = "address", ignore = true)
    UserPerson create(CreateUserPersonDTO dto);

    @Named("mapProfile")
    default UserProfileDTO mapProfile(UserProfile entity) {
        UserProfileDTO dto = map(entity);
        dto.getPerson().setModificationCount(entity.getModificationCount());
        dto.getAccountSettings().setModificationCount(entity.getModificationCount());

        return dto;
    }

    UserProfileDTO map(UserProfile entity);

    default UserPersonDTO mapUserPerson(UserProfile entity) {
        var dto = map(entity.getPerson());
        dto.setModificationCount(entity.getModificationCount());

        return dto;
    }

    @Mapping(target = "modificationCount", ignore = true)
    UserPersonDTO map(UserPerson entity);

    UserPersonAddressDTO map(UserPersonAddress entity);

    UserPersonPhoneDTO map(UserPersonPhone entity);

    default UserProfileAccountSettingsDTO mapAccountSettings(UserProfile entity) {
        var dto = map(entity.getAccountSettings());
        dto.setModificationCount(entity.getModificationCount());
        return dto;
    }

    @Mapping(target = "modificationCount", ignore = true)
    UserProfileAccountSettingsDTO map(UserProfileAccountSettings entity);

    default void updateUserPerson(UserProfile model, UpdateUserPersonRequestDTO dto) {
        model.setModificationCount(dto.getModificationCount());
        update(model.getPerson(), dto);
    }

    void update(@MappingTarget UserPerson model, UpdateUserPersonRequestDTO dto);

    UserPersonCriteria map(UserPersonCriteriaDTO dto);

    @Mapping(target = "removeStreamItem", ignore = true)
    @Mapping(target = "stream", qualifiedByName = "mapStream")
    UserProfilePageResultDTO mapPageResult(PageResult<UserProfile> page);

    @Named("mapStream")
    @IterableMapping(qualifiedByName = "mapProfile")
    List<UserProfileDTO> mapStream(Stream<UserProfile> stream);

    default void updateUserSettings(UserProfile model, UpdateUserSettingsDTO dto) {
        model.setModificationCount(dto.getModificationCount());
        update(model.getAccountSettings(), dto);
    }

    void update(@MappingTarget UserProfileAccountSettings model, UpdateUserSettingsDTO dto);

}
