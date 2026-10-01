// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.qualityflow;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** 质量整改协同应用与 UTC 时钟。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@SpringBootApplication(
    excludeName =
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class QualityFlowApplication {
  /** 启动质量整改协同服务。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
  public static void main(String[] args) {
    SpringApplication.run(QualityFlowApplication.class, args);
  }

  /** 注入可替换业务时钟用于期限验收。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
