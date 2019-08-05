package io.penguinstats.controller;

import io.penguinstats.enums.UploadCountType;
import io.penguinstats.model.*;
import io.penguinstats.service.ItemDropService;
import io.penguinstats.service.UserService;
import io.penguinstats.util.CookieUtil;
import io.penguinstats.util.IpUtil;
import io.penguinstats.util.JSONUtil;
import io.penguinstats.util.UserUtil;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/users")
public class UserController {

	public static final String INTERNAL_USER_ID_PREFIX = "internal_";

	private static Logger logger = LogManager.getLogger(UserController.class);

	@Autowired
	private UserService userService;

	@Autowired
	private ItemDropService itemDropService;

	@Autowired
	private UserUtil userUtil;

	@ApiOperation("Login")
	@PostMapping(produces = "text/plain;charset=UTF-8")
	public ResponseEntity<String> login(@RequestBody String userID, HttpServletRequest request,
			HttpServletResponse response) {
		try {
			boolean isInternal = false;
			if (userID.startsWith(INTERNAL_USER_ID_PREFIX)) {
				isInternal = true;
				userID = userID.substring(INTERNAL_USER_ID_PREFIX.length());
			}
			User user = userService.getUserByUserID(userID);
			if (user == null) {
				if (isInternal) {
					userID = userService.createNewUser(userID, IpUtil.getIpAddr(request));
					if (userID == null) {
						logger.error("Failed to create new user.");
						return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
					} else {
						userService.addTag(userID, "internal");
					}
				} else {
					return new ResponseEntity<>(HttpStatus.NOT_FOUND);
				}
			}
			CookieUtil.setUserIDCookie(response, userID);
			return new ResponseEntity<>(new JSONObject().put("userID", userID).toString(), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Error in getUser", e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@ApiOperation("")
	@PutMapping(path = "/weight", produces = "text/plain;charset=UTF-8")
	public ResponseEntity<String> updateUserWeight(@RequestParam(value = "upload_lower_count", defaultValue = "0", required = false) Integer uploadLowerCount,
												   @RequestParam(value = "upload_upper_count", required = false) Integer uploadUpperCount,
												   @RequestParam(value = "upload_count_type") String uploadCountType,
												   @RequestParam(value = "weight") Double weight) {
		try {
			logger.info("PUT /weight");
			if (UploadCountType.TOTAL_UPLOAD.getType().equals(uploadCountType)) {
				userService.updateWeightByUploadRange(uploadLowerCount, uploadUpperCount, UploadCountType.TOTAL_UPLOAD, weight);
			} else if (UploadCountType.RELIABLE_UPLOAD.getType().equals(uploadCountType)) {
				userService.updateWeightByUploadRange(uploadLowerCount, uploadUpperCount, UploadCountType.RELIABLE_UPLOAD, weight);
			} else {
				logger.error("Invalid uploadCountType: " + uploadCountType);
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}

			List<DropMatrixElement> elements = itemDropService.generateDropMatrixElements(null, true);
			JSONArray array = new JSONArray(elements);
			return new ResponseEntity<>(array.toString(), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Error in updateUserWeight", e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

}
