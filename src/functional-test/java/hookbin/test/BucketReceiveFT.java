package hookbin.test;

import static com.jayway.restassured.RestAssured.given;
import static hookbin.test.matcher.UrlMatcher.canFollow;
import static hookbin.test.matcher.UrlMatcher.isValid;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.Resource;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.jayway.restassured.http.ContentType;

import hookbin.model.Bucket;
import hookbin.spring.Application;
import hookbin.test.AbstractBucketTest;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class BucketReceiveFT extends AbstractBucketTest {
    
    Resource<Bucket> bucket;
        
    @Before
    public void setupBucket() throws JsonParseException, JsonMappingException, IOException {
         bucket = createBucket();
    }
    
    @After
    public void cleanupBucket() {
        given()
        .when()
            .delete(bucket.getLink("self").getHref())
        .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);
    }
    
    @Test
    public void nonExistentBucketShouldNotBeAbleToReceive() {
        given()
            .body(UUID.randomUUID().toString())
        .when()
            .post("../" + UUID.randomUUID().toString())
        .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }
    
    @Test
    public void bucketCanReceivePosts() {
        for (int i=1; i<=3; i++) {
            // post something to the bucket
            given()
                .body(UUID.randomUUID().toString())
            .when()
                .post(bucket.getLink("receive").getHref())
            .then()
                .statusCode(HttpStatus.SC_OK);
            
            given()
            .when()
                .get(bucket.getLink("self").getHref())
            .then()
                .statusCode(HttpStatus.SC_OK)
                .body("requestCount", equalTo(i))
                .body("_links.requests.href", allOf(isValid(), canFollow()));
        }
        
        // get the requestsUrl from the bucket
        String requestsUrl = given()
            .when()
                .get(bucket.getLink("self").getHref())
            .then()
                .statusCode(HttpStatus.SC_OK)
                .body("requestCount", equalTo(3))
                .body("_links.requests.href", allOf(isValid(), canFollow()))
                .extract().path("_links.requests.href");
        
        // follow the requests url to ensure it has three requests
        given()
        .when()
            .get(requestsUrl)
        .then()
            .statusCode(HttpStatus.SC_OK)
            .body("size()", equalTo(3));
    }
    
    @Test
    public void receivedPostsCanBeRetrieved() {
        String body = UUID.randomUUID().toString();
        String header = UUID.randomUUID().toString();
        
        // post something to the bucket
        given()
            .body(body)
            .header("x-test", header)
        .when()
            .post(bucket.getLink("receive").getHref())
        .then()
            .statusCode(HttpStatus.SC_OK);
        
        // get the requestsUrl from the bucket
        String requestsUrl = given()
            .when()
                .get(bucket.getLink("self").getHref())
            .then()
                .statusCode(HttpStatus.SC_OK)
                .body("requestCount", equalTo(1))
                .body("_links.requests.href", allOf(isValid(), canFollow()))
                .extract().path("_links.requests.href");
        
        // follow the requests url to get the last request received
        String requestUrl = given()
        .when()
            .get(requestsUrl)
        .then()
            .statusCode(HttpStatus.SC_OK)
            .body("size()", equalTo(1))
            .body("get(0)._links.self.href", allOf(isValid(), canFollow()))
            .extract().path("get(0)._links.self.href");
        
        // grab the request itself
        given()
            .accept(ContentType.JSON)
        .when()
            .get(requestUrl)
        .then()
            .log().all()
            .statusCode(HttpStatus.SC_OK)
            .body("_links.self.href", allOf(isValid(), canFollow()))
            .body("_links.bucket.href", allOf(isValid(), canFollow()))
            .body("requestId", notNullValue())
            .body("receivedTsEpoch", notNullValue())
            .body("receivedTs", notNullValue())
            .body("headers.x-test", equalTo(header))
            .body("body", equalTo(body));
    }

}
