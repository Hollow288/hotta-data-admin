package com.hollow.build;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HottaDataAdminApplicationTests {

    @Test
    void contextLoads() {
        String[] split = "".split(",");
        String[] split2 = "".split(",",-1);
        String[] split3 = ",,".split(",");
        String[] split4 = ",,".split(",",-1);
        System.out.println();


    }

}
