//package OrangeCloud.UserRepo.config;
//
//import OrangeCloud.UserRepo.filter.MdcLoggingFilter;
//import org.springframework.boot.web.servlet.FilterRegistrationBean;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class LoggingConfig {
//
//    @Bean
//    public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilter() {
//        FilterRegistrationBean<MdcLoggingFilter> registrationBean = new FilterRegistrationBean<>();
//        registrationBean.setFilter(new MdcLoggingFilter());
//        registrationBean.setOrder(1);
//        return registrationBean;
//    }
//}
