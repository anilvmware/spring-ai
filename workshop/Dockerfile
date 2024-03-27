FROM ghcr.io/vmware-tanzu-labs/educates-jdk17-environment:2.7
RUN  curl --fail -sL -o spring.tar.gz https://repo.spring.io/artifactory/milestone/org/springframework/boot/spring-boot-cli/1.2.0.M2/spring-boot-cli-1.2.0.M2-bin.tar.gz
RUN tar -xvf spring.tar.gz
RUN cd spring-1.2.0.M2
USER root
RUN ln -s ./shell-completion/bash/spring /etc/bash_completion.d/spring
USER 1001