package ch.uzh.ifi.hase.soprafs22.screwyourneighborserver.api;

import static ch.uzh.ifi.hase.soprafs22.screwyourneighborserver.util.SessionUtil.getSessionIdOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import ch.uzh.ifi.hase.soprafs22.screwyourneighborserver.entity.Game;
import ch.uzh.ifi.hase.soprafs22.screwyourneighborserver.entity.Player;
import ch.uzh.ifi.hase.soprafs22.screwyourneighborserver.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs22.screwyourneighborserver.repository.ParticipationRepository;
import ch.uzh.ifi.hase.soprafs22.screwyourneighborserver.repository.PlayerRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GameIntegrationTest {

  @LocalServerPort private int port;

  private WebTestClient webTestClient;

  @Autowired private GameRepository gameRepository;
  @Autowired private ParticipationRepository participationRepository;
  @Autowired private PlayerRepository playerRepository;

  private static final Player PLAYER_1 = new Player();
  private static final Game GAME_1 = new Game();
  private static final Game GAME_2 = new Game();
  private static final Game GAME_3 = new Game();

  @BeforeEach
  @AfterEach
  public void setup() {
    webTestClient =
        WebTestClient.bindToServer()
            .responseTimeout(Duration.ofMinutes(1))
            .baseUrl("http://localhost:" + port)
            .build();

    PLAYER_1.setName("player1");
    participationRepository.deleteAll();
    gameRepository.deleteAll();
    playerRepository.deleteAll();
  }

  @Test
  public void create_game_and_return_created_game_by_ID() {
    HttpHeaders responseHeaders =
        webTestClient
            .post()
            .uri("/players")
            .body(BodyInserters.fromValue(PLAYER_1))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .returnResult()
            .getResponseHeaders();

    String sessionId = getSessionIdOf(responseHeaders);

    GAME_1.setName("game_1");

    webTestClient
        .post()
        .uri("/games")
        .body(Mono.just(GAME_1), Game.class)
        .header(HttpHeaders.COOKIE, "JSESSIONID=%s".formatted(sessionId))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("name")
        .isEqualTo(GAME_1.getName())
        .jsonPath("_embedded.participations")
        .isNotEmpty()
        .jsonPath("_embedded.participations[0].player.name")
        .isEqualTo(PLAYER_1.getName());

    Long id = gameRepository.findAllByName("game_1").get(0).getId();
    String uri = "games/" + id.toString();

    webTestClient
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("name")
        .isEqualTo(GAME_1.getName())
        .jsonPath("_embedded.participations")
        .isNotEmpty()
        .jsonPath("_embedded.participations[0].player.name")
        .isEqualTo(PLAYER_1.getName());
  }

  @Test
  public void return_found_game_by_ID() {

    GAME_1.setName("My_Game");
    gameRepository.saveAll(List.of(GAME_1));

    Long id = gameRepository.findAllByName("My_Game").get(0).getId();
    String uri = "games/" + id.toString();

    var game1 =
        webTestClient
            .get()
            .uri(uri)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(Game.class)
            .returnResult()
            .getResponseBody();

    assertThat(game1, notNullValue());
    assertThat(game1.getName(), equalTo(GAME_1.getName()));
  }

  @Test
  public void return_not_found_game() {
    webTestClient.get().uri("/games/5").exchange().expectStatus().isNotFound();
  }

  @Test
  public void return_found_all_games() {

    GAME_1.setName("My_first_Game");
    GAME_2.setName("My_second_Game");
    GAME_3.setName("My_third_Game");

    var gamesList = List.of(GAME_1, GAME_2, GAME_3);
    gameRepository.saveAll(gamesList);

    var games =
        webTestClient
            .get()
            .uri("/games")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("_embedded.games")
            .value(hasSize(3));
  }
}