/*
 * TurnServer, the OpenSource Java Solution for TURN protocol. Maintained by the
 * Jitsi community (http://jitsi.org).
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package org.jitsi.turnserver.listeners;

import java.util.logging.*;

import org.ice4j.*;
import org.ice4j.attribute.*;
import org.ice4j.message.*;
import org.ice4j.stack.*;
import org.jitsi.turnserver.stack.Allocation;
import org.jitsi.turnserver.stack.FiveTuple;
import org.jitsi.turnserver.stack.TurnStack;

/**
 * The class that would be handling and responding to incoming Allocation
 * requests that are Allocation and sends a success or error response
 * 
 * @author Aakash Garg
 */
public class AllocationRequestListener
    implements RequestListener
{
    /**
     * The <tt>Logger</tt> used by the <tt>AllocationRequestListener</tt> class
     * and its instances for logging output.
     */
    private static final Logger logger = Logger
        .getLogger(AllocationRequestListener.class.getName());

    private final TurnStack turnStack;

    /**
     * The indicator which determines whether this
     * <tt>AllocationrequestListener</tt> is currently started.
     */
    private boolean started = false;

    /**
     * Creates a new AllocationRequestListener
     * 
     * @param turnStack
     */
    public AllocationRequestListener(StunStack stunStack)
    {
        if (stunStack instanceof TurnStack)
        {
            this.turnStack = (TurnStack) stunStack;
        }
        else
        {
            throw new IllegalArgumentException("This is not a TurnStack!");
        }
    }

    @Override
    public void processRequest(StunMessageEvent evt)
        throws IllegalArgumentException
    {
        if (logger.isLoggable(Level.FINER))
        {
            logger.finer("Received request " + evt);
        }
        Message message = evt.getMessage();
        if (message.getMessageType() == Message.ALLOCATE_REQUEST)
        {
            logger.setLevel(Level.FINEST);
            logger.finest("Received a Allocation Request");
            System.out.println("Received a Allocation Request");
            
            Response response = null;
            RequestedTransportAttribute requestedTransportAttribute =
                (RequestedTransportAttribute) message
                    .getAttribute(Attribute.REQUESTED_TRANSPORT);

            DontFragmentAttribute dontFragmentAttribute =
                (DontFragmentAttribute) message
                    .getAttribute(Attribute.DONT_FRAGMENT);

            ReservationTokenAttribute reservationTokenAttribute =
                (ReservationTokenAttribute) message
                    .getAttribute(Attribute.RESERVATION_TOKEN);

            LifetimeAttribute lifetimeAttribute = 
                (LifetimeAttribute) message.getAttribute(Attribute.LIFETIME);
            
            if (lifetimeAttribute == null)
            {
                lifetimeAttribute =
                    AttributeFactory
                        .createLifetimeAttribute(
                            (int) (Allocation.MAX_LIFETIME / 1000));
            }
            
            EvenPortAttribute evenPortAttribute =
                (EvenPortAttribute) message.getAttribute(Attribute.EVEN_PORT);
            
            TransportAddress clientAddress = evt.getRemoteAddress();
            TransportAddress serverAddress = evt.getLocalAddress();
            Transport transport = Transport.UDP;
            FiveTuple fiveTuple =
                new FiveTuple(clientAddress, serverAddress, transport);
              
            Character errorCode = null;
            if (requestedTransportAttribute == null)
            {
                errorCode = ErrorCodeAttribute.BAD_REQUEST;
            }
            else if (requestedTransportAttribute.getRequestedTransport() != 17)
            {
               System.out.println(requestedTransportAttribute.getRequestedTransport());
                errorCode = ErrorCodeAttribute.UNSUPPORTED_TRANSPORT_PROTOCOL;
            }
            else if (reservationTokenAttribute != null
                && evenPortAttribute != null)
            {
                errorCode = ErrorCodeAttribute.BAD_REQUEST;
            }
            
            if (turnStack.getServerAllocation(fiveTuple)!=null)
            {
                errorCode = ErrorCodeAttribute.ALLOCATION_MISMATCH;
            }
            // do other checks here
            
            if (errorCode == null)
            {
                // do the allocation with 5-tuple
                TransportAddress relayAddress = Allocation.getNewRelayAddress();
                logger.finest("Added a new Relay Address "+relayAddress);
                System.out.println("Added a new Relay Address "+relayAddress);
                
                Allocation allocation =
                    new Allocation(relayAddress, fiveTuple,
                        lifetimeAttribute.getLifetime());
                this.turnStack.addNewServerAllocation(allocation);
                logger.finest("Added a new Allocation");
                System.out.println("Added a new Allocation");
                
                response = MessageFactory.createAllocationResponse(
                     (Request) message,
                     allocation.getFiveTuple().getClientTransportAddress(), 
                     allocation.getRelayAddress(), 
                     (int) allocation.getLifetime());
            }
            else
            {
                
                logger.finest("Error Code " + errorCode
                    + " on Allocation Request");
                System.out.println("Error Code " + (int) errorCode
                    + " on Allocation Request");
                response =
                    MessageFactory.createAllocationErrorResponse(errorCode);
            }
            
            try
            {
                turnStack.sendResponse(
                    evt.getTransactionID().getBytes(), response,
                    evt.getLocalAddress(), evt.getRemoteAddress());
            }
            catch (Exception e)
            {
                logger.log(
                    Level.INFO, "Failed to send " + response + " through "
                        + evt.getLocalAddress(), e);
                // try to trigger a 500 response although if this one failed,
                throw new RuntimeException("Failed to send a response", e);
            }
        }
        else
        {
            return;
        }
    }

    /**
     * Starts this <tt>AllocationRequestListener</tt>. If it is not currently
     * running, does nothing.
     */
    public void start()
    {
        if (!started)
        {
            turnStack.addRequestListener(this);
            started = true;
        }
    }

    /**
     * Stops this <tt>AllocationRequestListenerr</tt>. A stopped
     * <tt>AllocationRequestListenerr</tt> can be restarted by calling
     * {@link #start()} on it.
     */
    public void stop()
    {
        turnStack.removeRequestListener(this);
        started = false;
    }
}
