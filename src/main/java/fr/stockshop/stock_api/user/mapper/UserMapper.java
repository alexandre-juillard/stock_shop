package fr.stockshop.stock_api.user.mapper;

import org.springframework.stereotype.Component;

import fr.stockshop.stock_api.user.dto.UserResponse;
import fr.stockshop.stock_api.user.entity.User;

@Component
public class UserMapper {

	public UserResponse toResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getEmail(),
				user.getFirstName(),
				user.getLastName(),
				user.getRole()
		);
	}
}

