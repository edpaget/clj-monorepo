(ns bashketball-game-ui.graphql.queries
  "GraphQL query definitions.

  Provides GraphQL queries for the bashketball-game-api."
  (:require
   ["@apollo/client" :as apollo]))

(def ME_QUERY
  "Query for the current authenticated user."
  (apollo/gql "
    query Me {
      me {
        id
        email
        name
        avatarUrl
      }
    }
  "))

(def MY_DECKS_QUERY
  "Query for the current user's decks."
  (apollo/gql "
    query MyDecks {
      myDecks {
        id
        name
        cardSlugs
        isValid
        validationErrors
      }
    }
  "))

(def CARD_FIELDS_FRAGMENT
  "Fragment for common card fields and type-specific fields."
  (apollo/gql "
    fragment CardFields on GameCard {
      __typename
      ... on PlayerCard {
        slug
        name
        setSlug
        cardType
        sht
        pss
        def
        speed
        size
        abilities
        deckSize
      }
      ... on AbilityCard {
        slug
        name
        setSlug
        cardType
        abilities
      }
      ... on PlayCard {
        slug
        name
        setSlug
        cardType
        fate
        play
      }
      ... on StandardActionCard {
        slug
        name
        setSlug
        cardType
        fate
        offense
        defense
      }
      ... on SplitPlayCard {
        slug
        name
        setSlug
        cardType
        fate
        offense
        defense
      }
      ... on CoachingCard {
        slug
        name
        setSlug
        cardType
        fate
        coaching
      }
      ... on TeamAssetCard {
        slug
        name
        setSlug
        cardType
        fate
        assetPower
      }
    }
  "))

(def DECK_QUERY
  "Query for a single deck with resolved cards."
  (apollo/gql "
    query Deck($id: Uuid!) {
      deck(id: $id) {
        id
        name
        cardSlugs
        cards {
          ...CardFields
        }
        isValid
        validationErrors
      }
    }
    fragment CardFields on GameCard {
      __typename
      ... on PlayerCard {
        slug
        name
        setSlug
        cardType
        sht
        pss
        def
        speed
        size
        abilities
        deckSize
      }
      ... on AbilityCard {
        slug
        name
        setSlug
        cardType
        abilities
      }
      ... on PlayCard {
        slug
        name
        setSlug
        cardType
        fate
        play
      }
      ... on StandardActionCard {
        slug
        name
        setSlug
        cardType
        fate
        offense
        defense
      }
      ... on SplitPlayCard {
        slug
        name
        setSlug
        cardType
        fate
        offense
        defense
      }
      ... on CoachingCard {
        slug
        name
        setSlug
        cardType
        fate
        coaching
      }
      ... on TeamAssetCard {
        slug
        name
        setSlug
        cardType
        fate
        assetPower
      }
    }
  "))

(def CARDS_QUERY
  "Query for card catalog with optional set filter."
  (apollo/gql "
    query Cards($setSlug: String) {
      cards(setSlug: $setSlug) {
        ...CardFields
      }
    }
    fragment CardFields on GameCard {
      __typename
      ... on PlayerCard {
        slug
        name
        setSlug
        cardType
        sht
        pss
        def
        speed
        size
        abilities
        deckSize
      }
      ... on AbilityCard {
        slug
        name
        setSlug
        cardType
        abilities
      }
      ... on PlayCard {
        slug
        name
        setSlug
        cardType
        fate
        play
      }
      ... on StandardActionCard {
        slug
        name
        setSlug
        cardType
        fate
        offense
        defense
      }
      ... on SplitPlayCard {
        slug
        name
        setSlug
        cardType
        fate
        offense
        defense
      }
      ... on CoachingCard {
        slug
        name
        setSlug
        cardType
        fate
        coaching
      }
      ... on TeamAssetCard {
        slug
        name
        setSlug
        cardType
        fate
        assetPower
      }
    }
  "))

(def SETS_QUERY
  "Query for available card sets."
  (apollo/gql "
    query Sets {
      sets {
        slug
        name
        description
      }
    }
  "))

(def CARD_QUERY
  "Query for a single card."
  (apollo/gql "
    query Card($slug: String!) {
      card(slug: $slug) {
        ...CardFields
      }
    }
    fragment CardFields on GameCard {
      __typename
      ... on PlayerCard {
        slug
        name
        setSlug
        cardType
        sht
        pss
        def
        speed
        size
        abilities
        deckSize
      }
      ... on AbilityCard {
        slug
        name
        setSlug
        cardType
        abilities
      }
      ... on PlayCard {
        slug
        name
        setSlug
        cardType
        fate
        play
      }
      ... on StandardActionCard {
        slug
        name
        setSlug
        cardType
        fate
        offense
        defense
      }
      ... on SplitPlayCard {
        slug
        name
        setSlug
        cardType
        fate
        offense
        defense
      }
      ... on CoachingCard {
        slug
        name
        setSlug
        cardType
        fate
        coaching
      }
      ... on TeamAssetCard {
        slug
        name
        setSlug
        cardType
        fate
        assetPower
      }
    }
  "))

;; ---------------------------------------------------------------------------
;; Game Queries

(def MY_GAMES_QUERY
  "Query for the current user's games with optional status filter and pagination."
  (apollo/gql "
    query MyGames($status: String, $limit: Int, $offset: Int) {
      myGames(status: $status, limit: $limit, offset: $offset) {
        data {
          id
          player1Id
          player2Id
          status
          createdAt
          startedAt
        }
        pageInfo {
          totalCount
          hasNextPage
          hasPreviousPage
        }
      }
    }
  "))

(def AVAILABLE_GAMES_QUERY
  "Query for games waiting for an opponent."
  (apollo/gql "
    query AvailableGames {
      availableGames {
        id
        player1Id
        status
        createdAt
      }
    }
  "))

(def GAME_QUERY
  "Query for a single game with full state."
  (apollo/gql "
    query Game($id: Uuid!) {
      game(id: $id) {
        id
        player1Id
        player2Id
        status
        gameState
        winnerId
        createdAt
        startedAt
      }
    }
  "))
