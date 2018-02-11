package se.gustavkarlsson.rocketchat.jira_trigger.messages;

import com.google.inject.*;
import se.gustavkarlsson.rocketchat.jira_trigger.configuration.JiraConfiguration;
import se.gustavkarlsson.rocketchat.jira_trigger.configuration.MessageConfiguration;
import se.gustavkarlsson.rocketchat.jira_trigger.di.annotations.Default;
import se.gustavkarlsson.rocketchat.jira_trigger.messages.field_creators.FieldCreator;

import java.util.List;

public class MessageModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(new TypeLiteral<List<FieldCreator>>() {
		}).annotatedWith(Default.class).toProvider(DefaultFieldCreatorsProvider.class);
		bind(new TypeLiteral<List<FieldCreator>>() {
		}).toProvider(ExtendedFieldCreatorsProvider.class);
	}

	@Provides
	ToRocketChatMessageFactory provideToRocketChatMessageFactory(MessageConfiguration messageConfig) {
		return new ToRocketChatMessageFactory(messageConfig.getUsername(), messageConfig.getIconUrl());
	}

	@Provides
	FieldCreatorMapper provideFieldCreatorMapper(MessageConfiguration messageConfig) {
		return new FieldCreatorMapper(messageConfig.isUseRealNames(), messageConfig.getDateFormatter());
	}

	@Provides
	AttachmentCreator provideAttachmentConverter(MessageConfiguration messageConfig, JiraConfiguration jiraConfig, @Default List<FieldCreator> defaultFieldCreators, List<FieldCreator> extendedFieldCreators) {
		return new AttachmentCreator(messageConfig.isPriorityColors(), messageConfig.getDefaultColor(), defaultFieldCreators, extendedFieldCreators, jiraConfig.getUri());
	}

	public static class ExtendedFieldCreatorsProvider implements Provider<List<FieldCreator>> {
		private final FieldCreatorMapper fieldCreatorMapper;
		private final MessageConfiguration messageConfig;

		@Inject
		public ExtendedFieldCreatorsProvider(FieldCreatorMapper fieldCreatorMapper, MessageConfiguration messageConfig) {
			this.fieldCreatorMapper = fieldCreatorMapper;
			this.messageConfig = messageConfig;
		}

		@Override
		public List<FieldCreator> get() {
			return fieldCreatorMapper.getCreators(messageConfig.getExtendedFields());
		}
	}
}
