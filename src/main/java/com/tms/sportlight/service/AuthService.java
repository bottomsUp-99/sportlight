package com.tms.sportlight.service;

import com.tms.sportlight.domain.User;
import com.tms.sportlight.repository.UserRepository;
import com.tms.sportlight.util.JWTUtil;
import com.tms.sportlight.security.util.VerificationCodeGenerator;
import com.tms.sportlight.security.auth.VerificationCode;
import jakarta.mail.MessagingException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final JWTUtil jwtUtil;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final VerificationCodeGenerator codeGenerator;
    private final Map<String, VerificationCode> verificationCodes = new HashMap<>();

    public AuthService(UserRepository userRepository, EmailService emailService, JWTUtil jwtUtil,
        BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.jwtUtil = jwtUtil;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.codeGenerator = new VerificationCodeGenerator();
    }

    /**
     * 사용자가 입력한 정보가 유효한 경우 인증 코드를 생성하고, 사용자 이메일로 전송
     *
     * @param loginId  사용자의 로그인 ID (이메일)
     * @param userName 사용자의 이름
     * @param userPhone 사용자의 전화번호
     * @return 정보가 일치하고 이메일 전송이 성공하면 true, 그렇지 않으면 false
     */
    public boolean sendPasswordResetLinkIfValid(String loginId, String userName, String userPhone) {
        Optional<User> userOpt = userRepository.findByLoginId(loginId);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getUserName().equals(userName) && user.getUserPhone().equals(userPhone)) {
                // JWT 생성
                String token = jwtUtil.createJwt(loginId, List.of("RESET_PASSWORD"),
                    Duration.ofMinutes(5).toMillis());
                String resetLink = "http://localhost:5173/password-reset?token=" + token;

                // 비밀번호 재설정 링크 이메일로 전송
                try {
                    String subject = "비밀번호 재설정 링크";
                    String content = "<p><h3>비밀번호를 재설정하려면 다음 링크를 클릭하세요:</h3></p><a href=\"" + resetLink + "\">비밀번호 재설정하기</a>";
                    emailService.sendEmail(user.getLoginId(), subject, content);
                } catch (MessagingException e) {
                    e.printStackTrace();
                    return false;
                }

                return true;
            }
        }
        return false;
    }

/*    public boolean sendVerificationCodeIfValid(String loginId, String userName, String userPhone) {
        Optional<User> userOpt = userRepository.findByLoginId(loginId);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getUserName().equals(userName) && user.getUserPhone().equals(userPhone)) {
                String code = codeGenerator.generateCode();
                VerificationCode verificationCode = new VerificationCode(code, 10); // 유효기간 10분 설정

                // 사용자 이메일로 인증 코드 전송
                try {
                    String subject = "비밀번호 재설정 인증 코드";
                    String content = "<p>비밀번호 재설정을 위한 인증 코드입니다:</p><h3>" + code + "</h3>";
                    emailService.sendEmail(user.getLoginId(), subject, content);
                } catch (MessagingException e) {
                    e.printStackTrace();
                    return false;
                }

                verificationCodes.put(loginId, verificationCode);
                return true;
            }
        }
        return false; // 사용자가 존재하지 않거나 정보가 일치하지 않음
    }*/

    /**
     * 입력된 코드가 저장된 인증 코드와 일치하고 유효한지 확인
     *
     * @param loginId   인증하려는 사용자의 로그인 ID
     * @param inputCode 사용자가 입력한 인증 코드
     * @return 코드가 유효하고 일치하면 true, 그렇지 않으면 false
     */
    public boolean verifyCode(String loginId, String inputCode) {
        VerificationCode storedCode = verificationCodes.get(loginId);
        return storedCode != null && storedCode.isValid(inputCode);
    }


    /**
     * JWT 토큰이 만료되었는지 검증
     *
     * @param token JWT 토큰
     */
    public boolean verifyToken(String token) {
        return !jwtUtil.isExpired(token);
    } // isValidToken? vs verifyToken?

    /**
     * JWT 토큰에서 사용자 ID를 추출하고 해당 사용자 비밀번호 업데이트
     *
     * @param token JWT 토큰
     * @param newPwd 변경될 새로운 비밀번호
     */
    public void updatePassword(String token, String newPwd) {

        String loginId = jwtUtil.getUsername(token);
        Optional<User> userData = userRepository.findByLoginId(loginId);

        if (userData.isPresent()) {
            User user = userData.get();

            String loginPwd = bCryptPasswordEncoder.encode(newPwd);

            user.updatePassword(loginPwd);
            user.userModTime();

            userRepository.save(user);
        } else {
            throw new IllegalArgumentException("유효하지 않은 사용자입니다.");
        }
    }

}

