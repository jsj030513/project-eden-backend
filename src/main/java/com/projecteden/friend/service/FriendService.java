package com.projecteden.friend.service;
import java.time.LocalDate; import java.util.List;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.friend.domain.*; import com.projecteden.friend.dto.*; import com.projecteden.friend.repository.FriendRepository;
import com.projecteden.notification.domain.NotificationType; import com.projecteden.notification.service.NotificationService;
import com.projecteden.profile.repository.ProfileRepository; import com.projecteden.ranking.repository.RankingRepository;
import com.projecteden.user.domain.User; import com.projecteden.user.repository.UserRepository;
@Service
public class FriendService {
	private final FriendRepository friends; private final UserRepository users; private final ProfileRepository profiles; private final RankingRepository rankings; private final NotificationService notifications;
	public FriendService(FriendRepository friends, UserRepository users, ProfileRepository profiles, RankingRepository rankings, NotificationService notifications) { this.friends=friends; this.users=users; this.profiles=profiles; this.rankings=rankings; this.notifications=notifications; }
	@Transactional public FriendResponseDTO request(Long userId, FriendRequestDTO dto) { User requester=user(userId); User receiver=findTarget(dto); if(requester.getId().equals(receiver.getId())) throw new IllegalArgumentException("자기 자신에게 친구 요청을 보낼 수 없습니다."); if(existsEither(requester,receiver)) throw new IllegalArgumentException("이미 친구이거나 요청이 진행 중입니다."); return response(friends.save(Friend.create(requester,receiver)),receiver); }
	@Transactional public FriendResponseDTO accept(Long userId, Long requestId) { Friend f=friend(requestId); if(!f.getReceiver().getId().equals(userId)) throw new IllegalArgumentException("친구 요청을 수락할 권한이 없습니다."); if(f.getStatus()!=FriendStatus.PENDING) throw new IllegalArgumentException("이미 처리된 친구 요청입니다."); f.accept(); notifications.create(f.getRequester(), NotificationType.FRIEND_ADDED, f.getReceiver().getNickname()+"님과 친구가 되었습니다."); notifications.create(f.getReceiver(), NotificationType.FRIEND_ADDED, f.getRequester().getNickname()+"님과 친구가 되었습니다."); return response(f,f.getRequester()); }
	@Transactional public void delete(Long userId, Long friendshipId) { Friend f=friend(friendshipId); if(!f.getRequester().getId().equals(userId)&&!f.getReceiver().getId().equals(userId)) throw new IllegalArgumentException("친구 관계를 삭제할 권한이 없습니다."); friends.delete(f); }
	@Transactional(readOnly=true) public List<FriendResponseDTO> list(Long userId) { User me=user(userId); return friends.findByRequesterOrReceiverAndStatus(me,me,FriendStatus.ACCEPTED).stream().map(f->response(f,other(f,me))).toList(); }
	@Transactional(readOnly=true) public List<FriendResponseDTO> pending(Long userId) { User me=user(userId); return friends.findByReceiverAndStatus(me,FriendStatus.PENDING).stream().map(f->response(f,f.getRequester())).toList(); }
	public boolean areFriends(User a, User b) { return friends.findByRequesterOrReceiverAndStatus(a,a,FriendStatus.ACCEPTED).stream().anyMatch(f->other(f,a).getId().equals(b.getId())); }
	private boolean existsEither(User a,User b){return friends.existsByRequesterAndReceiver(a,b)||friends.existsByRequesterAndReceiver(b,a);}
	private User findTarget(FriendRequestDTO dto){ if(dto.nickname()!=null&&!dto.nickname().isBlank()) return users.findByNickname(dto.nickname()).orElseThrow(()->new ResourceNotFoundException("사용자를 찾을 수 없습니다.")); if(dto.friendCode()!=null&&!dto.friendCode().isBlank()){String c=dto.friendCode(); try{return user(Long.valueOf(c));}catch(NumberFormatException e){return users.findByEmail(c).orElseThrow(()->new ResourceNotFoundException("사용자를 찾을 수 없습니다."));}} throw new IllegalArgumentException("friendCode 또는 nickname이 필요합니다."); }
	private Friend friend(Long id){return friends.findById(id).orElseThrow(()->new ResourceNotFoundException("친구 요청을 찾을 수 없습니다."));}
	private User user(Long id){return users.findById(id).orElseThrow(()->new ResourceNotFoundException("사용자를 찾을 수 없습니다."));}
	private User other(Friend f,User me){return f.getRequester().getId().equals(me.getId())?f.getReceiver():f.getRequester();}
	private FriendResponseDTO response(Friend f,User u){var p=profiles.findByUser(u).orElse(null);var r=rankings.findByUser(u).orElse(null);return new FriendResponseDTO(f.getId(),u.getId(),u.getNickname(),p==null?null:p.getAvatarUrl(),p==null?"SPRING":p.getCurrentSeason(),u.getLastLoginAt()!=null&&u.getLastLoginAt().toLocalDate().equals(LocalDate.now()),r==null?0:r.getConsecutiveLogins(),u.getLastLoginAt(),f.getStatus());}
}
