(ns bashketball-editor-ui.graphql.queries
  "GraphQL query and mutation definitions.

  Stub queries for the bashketball-editor-api. These will be implemented
  once the backend card API is complete."
  (:require
   ["@apollo/client" :as apollo]))

(def ME_QUERY
  "Query for the current authenticated user."
  (apollo/gql "
    query Me {
      me {
        id
        githubLogin
        email
        avatarUrl
        name
      }
    }
  "))

(def CARDS_QUERY
  "Query for listing cards, optionally filtered by set."
  (apollo/gql "
    query Cards($setSlug: String, $cardType: CardType) {
      cards(setSlug: $setSlug, cardType: $cardType) {
        data {
          ... on PlayerCard {
            slug
            setSlug
            name
            updatedAt
          }
          ... on PlayCard {
            slug
            setSlug
            name
            updatedAt
            fate
          }
          ... on AbilityCard {
            slug
            setSlug
            name
            updatedAt
            fate
          }
          ... on SplitPlayCard {
            slug
            setSlug
            name
            updatedAt
            fate
          }
          ... on CoachingCard {
            slug
            setSlug
            name
            updatedAt
            fate
          }
          ... on TeamAssetCard {
            slug
            setSlug
            name
            updatedAt
            fate
          }
          ... on StandardActionCard {
            slug
            setSlug
            name
            updatedAt
            fate
          }
        }
      }
    }
  "))

(def CARD_QUERY
  "Query for a single card by slug and setSlug with all type-specific fields."
  (apollo/gql "
    query Card($slug: String!, $setSlug: String!) {
      card(slug: $slug, setSlug: $setSlug) {
        ... on PlayerCard {
          slug
          name
          setSlug
          imagePrompt
          cardSubtypes
          sht
          pss
          def
          speed
          size
          deckSize
          abilities
          playerSubtypes
        }
        ... on PlayCard {
          slug
          name
          setSlug
          imagePrompt
          cardSubtypes
          fate
          play
        }
        ... on AbilityCard {
          slug
          name
          setSlug
          imagePrompt
          cardSubtypes
          fate
          abilities
        }
        ... on SplitPlayCard {
          slug
          name
          setSlug
          imagePrompt
          cardSubtypes
          fate
          offense
          defense
        }
        ... on CoachingCard {
          slug
          name
          setSlug
          imagePrompt
          cardSubtypes
          fate
          coaching
        }
        ... on TeamAssetCard {
          slug
          name
          setSlug
          imagePrompt
          cardSubtypes
          fate
          assetPower
        }
        ... on StandardActionCard {
          slug
          name
          setSlug
          imagePrompt
          cardSubtypes
          fate
          offense
          defense
        }
      }
    }
  "))

(def CARD_SETS_QUERY
  "Query for list sets."
  (apollo/gql "
    query CardSets {
      cardSets {
        data {
          slug
          name
          createdAt
          updatedAt
        }
      }
    }
"))

(def CARD_SET_QUERY
  "Query for getting a single set by its slug"
  (apollo/gql "
    query CardSet($slug: String!) {
      cardSet(slug: $slug) {
        slug
        name
        createdAt
        updatedAt
      }
    }
"))

;; -----------------------------------------------------------------------------
;; Mutations
;; -----------------------------------------------------------------------------

(def PULL_FROM_REMOTE_MUTATION
  "Mutation to pull changes from the remote Git repository."
  (apollo/gql "
    mutation PullFromRemote {
      pullFromRemote {
        status
        message
        error
        conflicts
      }
    }
"))

(def PUSH_TO_REMOTE_MUTATION
  "Mutation to push changes to the remote Git repository."
  (apollo/gql "
    mutation PushToRemote {
      pushToRemote {
        status
        message
        error
        conflicts
      }
    }
"))

(def COMMIT_CHANGES_MUTATION
  "Mutation to commit staged changes."
  (apollo/gql "
    mutation CommitChanges($message: String) {
      commitChanges(message: $message) {
        success
        message
        commitId
      }
    }
"))

;; -----------------------------------------------------------------------------
;; Git Status Queries
;; -----------------------------------------------------------------------------

(def SYNC_STATUS_QUERY
  "Query for repository sync status with remote."
  (apollo/gql "
    query SyncStatus {
      syncStatus {
        ahead
        behind
        uncommittedChanges
        isClean
      }
    }
"))

(def WORKING_TREE_STATUS_QUERY
  "Query for detailed working tree status."
  (apollo/gql "
    query WorkingTreeStatus {
      workingTreeStatus {
        isDirty
        added
        modified
        deleted
        untracked
      }
    }
"))

(def BRANCH_INFO_QUERY
  "Query for current branch and all available branches."
  (apollo/gql "
    query BranchInfo {
      branchInfo {
        currentBranch
        branches
      }
    }
"))

;; -----------------------------------------------------------------------------
;; Branch & Discard Mutations
;; -----------------------------------------------------------------------------

(def SWITCH_BRANCH_MUTATION
  "Mutation to switch to an existing branch."
  (apollo/gql "
    mutation SwitchBranch($branch: String!) {
      switchBranch(branch: $branch) {
        status
        message
        branch
      }
    }
"))

(def CREATE_BRANCH_MUTATION
  "Mutation to create and switch to a new branch."
  (apollo/gql "
    mutation CreateBranch($branch: String!) {
      createBranch(branch: $branch) {
        status
        message
        branch
      }
    }
"))

(def DISCARD_CHANGES_MUTATION
  "Mutation to discard all uncommitted changes."
  (apollo/gql "
    mutation DiscardChanges {
      discardChanges {
        status
        message
        error
      }
    }
"))

;; -----------------------------------------------------------------------------
;; Card Create Mutations
;; -----------------------------------------------------------------------------

(def CREATE_PLAYER_CARD_MUTATION
  (apollo/gql "
    mutation CreatePlayerCard($setSlug: String!, $input: PlayerCardInput!) {
      createPlayerCard(setSlug: $setSlug, input: $input) {
        slug
        name
        setSlug
      }
    }
"))

(def CREATE_PLAY_CARD_MUTATION
  (apollo/gql "
    mutation CreatePlayCard($setSlug: String!, $input: PlayCardInput!) {
      createPlayCard(setSlug: $setSlug, input: $input) {
        slug
        name
        setSlug
      }
    }
"))

(def CREATE_ABILITY_CARD_MUTATION
  (apollo/gql "
    mutation CreateAbilityCard($setSlug: String!, $input: AbilityCardInput!) {
      createAbilityCard(setSlug: $setSlug, input: $input) {
        slug
        name
        setSlug
      }
    }
"))

(def CREATE_SPLIT_PLAY_CARD_MUTATION
  (apollo/gql "
    mutation CreateSplitPlayCard($setSlug: String!, $input: SplitPlayCardInput!) {
      createSplitPlayCard(setSlug: $setSlug, input: $input) {
        slug
        name
        setSlug
      }
    }
"))

(def CREATE_COACHING_CARD_MUTATION
  (apollo/gql "
    mutation CreateCoachingCard($setSlug: String!, $input: CoachingCardInput!) {
      createCoachingCard(setSlug: $setSlug, input: $input) {
        slug
        name
        setSlug
      }
    }
"))

(def CREATE_TEAM_ASSET_CARD_MUTATION
  (apollo/gql "
    mutation CreateTeamAssetCard($setSlug: String!, $input: TeamAssetCardInput!) {
      createTeamAssetCard(setSlug: $setSlug, input: $input) {
        slug
        name
        setSlug
      }
    }
"))

(def CREATE_STANDARD_ACTION_CARD_MUTATION
  (apollo/gql "
    mutation CreateStandardActionCard($setSlug: String!, $input: StandardActionCardInput!) {
      createStandardActionCard(setSlug: $setSlug, input: $input) {
        slug
        name
        setSlug
      }
    }
"))

;; -----------------------------------------------------------------------------
;; Card Update/Delete Mutations
;; -----------------------------------------------------------------------------

(def UPDATE_CARD_MUTATION
  (apollo/gql "
    mutation UpdateCard($slug: String!, $setSlug: String!, $input: CardUpdateInput!) {
      updateCard(slug: $slug, setSlug: $setSlug, input: $input) {
        ... on PlayerCard {
          slug
          name
          setSlug
        }
        ... on PlayCard {
          slug
          name
          setSlug
        }
        ... on AbilityCard {
          slug
          name
          setSlug
        }
        ... on SplitPlayCard {
          slug
          name
          setSlug
        }
        ... on CoachingCard {
          slug
          name
          setSlug
        }
        ... on TeamAssetCard {
          slug
          name
          setSlug
        }
        ... on StandardActionCard {
          slug
          name
          setSlug
        }
      }
    }
"))

(def DELETE_CARD_MUTATION
  (apollo/gql "
    mutation DeleteCard($slug: String!, $setSlug: String!) {
      deleteCard(slug: $slug, setSlug: $setSlug)
    }
"))

;; -----------------------------------------------------------------------------
;; Set Mutations
;; -----------------------------------------------------------------------------

(def CREATE_CARD_SET_MUTATION
  "Mutation to create a new card set."
  (apollo/gql "
    mutation CreateCardSet($input: CardSetInput!) {
      createCardSet(input: $input) {
        slug
        name
        description
      }
    }
"))

(def DELETE_CARD_SET_MUTATION
  "Mutation to delete a card set and all its cards."
  (apollo/gql "
    mutation DeleteCardSet($slug: String!) {
      deleteCardSet(slug: $slug)
    }
"))

(def create-mutation-for-type
  "Map of card type to create mutation."
  {"PLAYER_CARD" CREATE_PLAYER_CARD_MUTATION
   "PLAY_CARD" CREATE_PLAY_CARD_MUTATION
   "ABILITY_CARD" CREATE_ABILITY_CARD_MUTATION
   "SPLIT_PLAY_CARD" CREATE_SPLIT_PLAY_CARD_MUTATION
   "COACHING_CARD" CREATE_COACHING_CARD_MUTATION
   "TEAM_ASSET_CARD" CREATE_TEAM_ASSET_CARD_MUTATION
   "STANDARD_ACTION_CARD" CREATE_STANDARD_ACTION_CARD_MUTATION})
