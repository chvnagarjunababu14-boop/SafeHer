import { expect } from "chai";
import { handleCreateGuardianInvite, handleAcceptGuardianInvite, handleRevokeGuardianRelationship } from "../src/invitations";
import { handleDispatchRemoteSos } from "../src/sos";
import { handleReportAlertReceived, handleAcknowledgeAlert } from "../src/acknowledgements";

describe("SafeHer Cloud Functions Backend Unit Tests", () => {
  describe("Authentication Requirements", () => {
    it("createGuardianInvite throws unauthenticated when request.auth is missing", async () => {
      try {
        await handleCreateGuardianInvite({ auth: null } as any);
        expect.fail("Should have thrown");
      } catch (err: any) {
        expect(err.code).to.equal("unauthenticated");
      }
    });

    it("acceptGuardianInvite throws unauthenticated when request.auth is missing", async () => {
      try {
        await handleAcceptGuardianInvite({ auth: null, data: { code: "ABCDEF12" } } as any);
        expect.fail("Should have thrown");
      } catch (err: any) {
        expect(err.code).to.equal("unauthenticated");
      }
    });

    it("revokeGuardianRelationship throws unauthenticated when request.auth is missing", async () => {
      try {
        await handleRevokeGuardianRelationship({ auth: null, data: { relationshipId: "rel1" } } as any);
        expect.fail("Should have thrown");
      } catch (err: any) {
        expect(err.code).to.equal("unauthenticated");
      }
    });

    it("dispatchRemoteSos throws unauthenticated when request.auth is missing", async () => {
      try {
        await handleDispatchRemoteSos({ auth: null, data: { idempotencyKey: "key1", recipientGuardianUids: ["u1"] } } as any);
        expect.fail("Should have thrown");
      } catch (err: any) {
        expect(err.code).to.equal("unauthenticated");
      }
    });

    it("reportAlertReceived throws unauthenticated when request.auth is missing", async () => {
      try {
        await handleReportAlertReceived({ auth: null, data: { alertId: "a1" } } as any);
        expect.fail("Should have thrown");
      } catch (err: any) {
        expect(err.code).to.equal("unauthenticated");
      }
    });

    it("acknowledgeAlert throws unauthenticated when request.auth is missing", async () => {
      try {
        await handleAcknowledgeAlert({ auth: null, data: { alertId: "a1" } } as any);
        expect.fail("Should have thrown");
      } catch (err: any) {
        expect(err.code).to.equal("unauthenticated");
      }
    });
  });

  describe("Input Validation & Error Handling", () => {
    it("acceptGuardianInvite throws invalid-argument for empty or short codes", async () => {
      const auth = { uid: "user1", token: { email: "u1@safeher.test" } };
      try {
        await handleAcceptGuardianInvite({ auth, data: { code: "123" } } as any);
        expect.fail("Should have thrown");
      } catch (err: any) {
        expect(err.code).to.equal("invalid-argument");
      }
    });

    it("revokeGuardianRelationship throws invalid-argument for missing relationshipId", async () => {
      const auth = { uid: "user1", token: { email: "u1@safeher.test" } };
      try {
        await handleRevokeGuardianRelationship({ auth, data: {} } as any);
        expect.fail("Should have thrown");
      } catch (err: any) {
        expect(err.code).to.equal("invalid-argument");
      }
    });

    it("dispatchRemoteSos throws invalid-argument for missing idempotencyKey", async () => {
      const auth = { uid: "user1", token: { email: "u1@safeher.test" } };
      try {
        await handleDispatchRemoteSos({ auth, data: { idempotencyKey: "", recipientGuardianUids: ["g1"] } } as any);
        expect.fail("Should have thrown");
      } catch (err: any) {
        expect(err.code).to.equal("invalid-argument");
      }
    });

    it("dispatchRemoteSos throws invalid-argument for empty recipients array", async () => {
      const auth = { uid: "user1", token: { email: "u1@safeher.test" } };
      try {
        await handleDispatchRemoteSos({ auth, data: { idempotencyKey: "test-key", recipientGuardianUids: [] } } as any);
        expect.fail("Should have thrown");
      } catch (err: any) {
        expect(err.code).to.equal("invalid-argument");
      }
    });

    it("reportAlertReceived throws invalid-argument for empty alertId", async () => {
      const auth = { uid: "user1", token: { email: "u1@safeher.test" } };
      try {
        await handleReportAlertReceived({ auth, data: { alertId: "" } } as any);
        expect.fail("Should have thrown");
      } catch (err: any) {
        expect(err.code).to.equal("invalid-argument");
      }
    });

    it("acknowledgeAlert throws invalid-argument for empty alertId", async () => {
      const auth = { uid: "user1", token: { email: "u1@safeher.test" } };
      try {
        await handleAcknowledgeAlert({ auth, data: { alertId: "" } } as any);
        expect.fail("Should have thrown");
      } catch (err: any) {
        expect(err.code).to.equal("invalid-argument");
      }
    });
  });
});
