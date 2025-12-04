package OrangeCloud.UserRepo.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Around("within(com.wealist.user.controller..*) || within(com.wealist.user.service..*)")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        long start = System.currentTimeMillis();
        log.debug("[{}.{}] Request - Args: {}", className, methodName, joinPoint.getArgs());

        Object result = joinPoint.proceed();

        long elapsedTime = System.currentTimeMillis() - start;
        log.debug("[{}.{}] Response - Return: {} ({}ms)", className, methodName, result, elapsedTime);

        return result;
    }
}
