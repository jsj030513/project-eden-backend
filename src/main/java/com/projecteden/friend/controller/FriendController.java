package com.projecteden.friend.controller;
import java.util.List; import org.springframework.http.*; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
import com.projecteden.friend.dto.*; import com.projecteden.friend.service.FriendService; import com.projecteden.user.domain.User;
@RestController @RequestMapping("/api/friends")
public class FriendController {
	private final FriendService service; public FriendController(FriendService service){this.service=service;}
	@PostMapping public ResponseEntity<FriendResponseDTO> request(@AuthenticationPrincipal User u,@RequestBody FriendRequestDTO d){return ResponseEntity.status(HttpStatus.CREATED).body(service.request(u.getId(),d));}
	@PutMapping("/{id}/accept") public FriendResponseDTO accept(@AuthenticationPrincipal User u,@PathVariable Long id){return service.accept(u.getId(),id);}
	@DeleteMapping("/{id}") public ResponseEntity<Void> delete(@AuthenticationPrincipal User u,@PathVariable Long id){service.delete(u.getId(),id);return ResponseEntity.noContent().build();}
	@GetMapping public List<FriendResponseDTO> list(@AuthenticationPrincipal User u){return service.list(u.getId());}
	@GetMapping("/pending") public List<FriendResponseDTO> pending(@AuthenticationPrincipal User u){return service.pending(u.getId());}
}
