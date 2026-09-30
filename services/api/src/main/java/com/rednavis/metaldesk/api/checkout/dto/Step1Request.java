package com.rednavis.metaldesk.api.checkout.dto;

/**
 * Checkout step 1 as a form submits it (BRD FR-4.1 to FR-4.3). It always carries the <em>full</em>
 * field set: the saved delivery address that pre-fills the form is offered separately and is never
 * merged in here, so a value the customer edited cannot be silently replaced.
 *
 * <p>Privacy acceptance is its own field, {@code privacyPolicyAccepted}, with the version accepted
 * beside it. There is no field that stands for several consents, and an absent or false value is
 * refused, never defaulted.
 *
 * @param name the customer's name; mandatory
 * @param contact the email address and phone number; both mandatory
 * @param street the street line of the delivery address; mandatory
 * @param city the city; mandatory
 * @param country the two-letter country code; mandatory
 * @param postalCode the postal code; mandatory
 * @param company the optional company name and address
 * @param note the order note; optional
 * @param privacyPolicyAccepted whether the privacy policy is accepted; must be true
 * @param policyVersion the version of the policy that was shown and accepted
 * @param account whether a guest wants the checkout converted into an account, and the password
 * @param locale the language tag for the verification mail; optional
 */
public record Step1Request(
    String name,
    Contact contact,
    String street,
    String city,
    String country,
    String postalCode,
    Company company,
    String note,
    Boolean privacyPolicyAccepted,
    String policyVersion,
    Account account,
    String locale) {

  /**
   * How to reach the customer.
   *
   * @param email the email address
   * @param phone the phone number
   */
  public record Contact(String email, String phone) {}

  /**
   * The optional company details.
   *
   * @param name the company name
   * @param address the company's address
   */
  public record Company(String name, String address) {}

  /**
   * A guest's request to turn the checkout into an account (BRD FR-4.2, "remember me").
   *
   * @param rememberMe whether they want an account
   * @param password the new account's password, needed only when {@code rememberMe} is true
   */
  public record Account(boolean rememberMe, String password) {

    /** Omits the password, so a logged request does not leak it. */
    @Override
    public String toString() {
      return "Account[rememberMe=" + rememberMe + ", password=***]";
    }
  }
}
